package com.campusute.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.SummarizeRequestDto
import com.campusute.app.core.network.SummarizeResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** null id = tạo mới; ngược lại đang sửa note đã có. */
data class NoteDraft(val id: String? = null, val title: String = "", val content: String = "")

/**
 * Notes CRUD (remote-first — closeout plan Ruling R-A) + AI "tóm tắt"
 * PROPOSE-ONLY (Ruling R-B): summarize chỉ đặt [proposal]; nội dung draft chỉ
 * được thay đổi khi user bấm "Chèn vào ghi chú" (acceptProposal). Không có
 * đường nào khác ghi vào draft từ kết quả AI.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val api: CampusApi,
) : ViewModel() {

    private val _notes = MutableStateFlow<List<NoteDto>>(emptyList())
    val notes: StateFlow<List<NoteDto>> = _notes.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()

    private val _proposal = MutableStateFlow<SummarizeResponseDto?>(null)
    val proposal: StateFlow<SummarizeResponseDto?> = _proposal.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _summarizing = MutableStateFlow(false)
    val summarizing: StateFlow<Boolean> = _summarizing.asStateFlow()

    /**
     * An AI failure is not a save failure. Sharing [error] made "tóm tắt hỏng" render in the same
     * slot as "không tải được danh sách", so the retry button reloaded the list instead of
     * re-asking the assistant.
     */
    private val _summarizeError = MutableStateFlow<String?>(null)
    val summarizeError: StateFlow<String?> = _summarizeError.asStateFlow()

    /**
     * Closing an editor that still holds unsaved text used to discard it silently. The flag is
     * recomputed against the snapshot taken when the editor opened, not against a keystroke count,
     * so typing and then deleting returns to "clean".
     */
    private var origin = NoteDraft()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { api.notes() }
                .onSuccess { envelope ->
                    when {
                        envelope.error != null -> _error.value = envelope.error.message
                        envelope.data == null -> _error.value = "Phản hồi không có dữ liệu."
                        else -> {
                            _notes.value = envelope.data
                            _error.value = null
                        }
                    }
                }
                .onFailure { _error.value = "Không thể tải ghi chú — kiểm tra kết nối." }
            _loading.value = false
        }
    }

    fun openNew() {
        origin = NoteDraft()
        _draft.value = NoteDraft()
        _proposal.value = null
        _summarizeError.value = null
        _dirty.value = false
    }

    fun openEdit(note: NoteDto) {
        origin = NoteDraft(id = note.id, title = note.title, content = note.content)
        _draft.value = origin
        _proposal.value = null
        _summarizeError.value = null
        _dirty.value = false
    }

    fun closeEditor() {
        _draft.value = null
        _proposal.value = null
        _summarizeError.value = null
        _dirty.value = false
    }

    fun onTitleChange(value: String) {
        _draft.value = _draft.value?.copy(title = value)
        recomputeDirty()
    }

    fun onContentChange(value: String) {
        _draft.value = _draft.value?.copy(content = value)
        recomputeDirty()
    }

    private fun recomputeDirty() {
        val d = _draft.value
        _dirty.value = d != null && (d.title != origin.title || d.content != origin.content)
    }

    fun save() {
        val d = _draft.value ?: return
        if (d.title.isBlank()) {
            _error.value = "Tiêu đề không được trống."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val request = NoteRequestDto(d.title.trim(), d.content)
            runCatching {
                if (d.id == null) api.createNote(request) else api.updateNote(d.id, request)
            }
                .onSuccess { envelope ->
                    val saved = envelope.data
                    when {
                        envelope.error != null -> _error.value = envelope.error.message
                        saved == null -> _error.value = "Không lưu được ghi chú — thử lại."
                        else -> {
                            _notes.value =
                                if (d.id == null) listOf(saved) + _notes.value
                                else _notes.value.map { if (it.id == saved.id) saved else it }
                            _draft.value = null
                            _proposal.value = null
                            _summarizeError.value = null
                            _dirty.value = false
                            _error.value = null
                        }
                    }
                }
                .onFailure { _error.value = "Không lưu được ghi chú — thử lại." }
            _busy.value = false
        }
    }

    fun delete(note: NoteDto) {
        viewModelScope.launch {
            runCatching { api.deleteNote(note.id) }
                .onSuccess {
                    _notes.value = _notes.value.filterNot { it.id == note.id }
                    _error.value = null
                }
                .onFailure { _error.value = "Không xóa được ghi chú." }
        }
    }

    fun summarize() {
        val d = _draft.value ?: return
        if (d.content.isBlank() || _summarizing.value) return
        viewModelScope.launch {
            _summarizing.value = true
            _summarizeError.value = null
            runCatching { api.aiSummarize(SummarizeRequestDto(d.title, d.content)) }
                .onSuccess { envelope ->
                    // A 200 carrying `error` is a refusal, not an empty summary: without this
                    // branch the dialog would have opened with "(AI không tạo được tóm tắt)".
                    val data = envelope.data
                    when {
                        envelope.error != null -> _summarizeError.value = envelope.error.message
                        data == null -> _summarizeError.value = "AI trả về phản hồi không hợp lệ."
                        else -> _proposal.value = data
                    }
                }
                .onFailure { _summarizeError.value = "AI tóm tắt tạm không khả dụng." }
            _summarizing.value = false
        }
    }

    /** Điểm duy nhất nội dung draft được thay từ kết quả AI — sau xác nhận của user. */
    fun acceptProposal() {
        val p = _proposal.value ?: return
        val d = _draft.value ?: return
        _draft.value = d.copy(content = d.content + "\n\n[Tóm tắt AI]\n" + p.summary)
        _proposal.value = null
        recomputeDirty()
    }

    fun dismissProposal() {
        _proposal.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
