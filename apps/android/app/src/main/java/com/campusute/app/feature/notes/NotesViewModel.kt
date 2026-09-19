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

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { api.notes() }
                .onSuccess { envelope ->
                    _notes.value = envelope.data ?: emptyList()
                    _error.value = null
                }
                .onFailure { _error.value = "Không thể tải ghi chú — kiểm tra kết nối." }
            _loading.value = false
        }
    }

    fun openNew() {
        _draft.value = NoteDraft()
        _proposal.value = null
    }

    fun openEdit(note: NoteDto) {
        _draft.value = NoteDraft(id = note.id, title = note.title, content = note.content)
        _proposal.value = null
    }

    fun closeEditor() {
        _draft.value = null
        _proposal.value = null
    }

    fun onTitleChange(value: String) {
        _draft.value = _draft.value?.copy(title = value)
    }

    fun onContentChange(value: String) {
        _draft.value = _draft.value?.copy(content = value)
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
                    val saved = envelope.data ?: return@onSuccess
                    _notes.value =
                        if (d.id == null) listOf(saved) + _notes.value
                        else _notes.value.map { if (it.id == saved.id) saved else it }
                    _draft.value = null
                    _proposal.value = null
                    _error.value = null
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
            runCatching { api.aiSummarize(SummarizeRequestDto(d.title, d.content)) }
                .onSuccess { envelope -> envelope.data?.let { _proposal.value = it } }
                .onFailure { _error.value = "AI tóm tắt tạm không khả dụng." }
            _summarizing.value = false
        }
    }

    /** Điểm duy nhất nội dung draft được thay từ kết quả AI — sau xác nhận của user. */
    fun acceptProposal() {
        val p = _proposal.value ?: return
        val d = _draft.value ?: return
        _draft.value = d.copy(content = d.content + "\n\n[Tóm tắt AI]\n" + p.summary)
        _proposal.value = null
    }

    fun dismissProposal() {
        _proposal.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
