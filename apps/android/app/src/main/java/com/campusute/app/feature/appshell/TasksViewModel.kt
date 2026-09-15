package com.campusute.app.feature.appshell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.TasksRepository
import com.campusute.app.core.database.StudyTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val newTitle: String = "",
    val syncing: Boolean = false,
    val offline: Boolean = false,
    val conflicts: Int = 0,
    val pending: Int = 0,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasksRepository: TasksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    val tasks: StateFlow<List<StudyTaskEntity>> = tasksRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        sync()
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(newTitle = value) }

    fun addTask() {
        val title = _uiState.value.newTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            tasksRepository.createTask(title, dueDate = null)
            _uiState.update { it.copy(newTitle = "") }
        }
    }

    fun toggleDone(task: StudyTaskEntity) {
        viewModelScope.launch { tasksRepository.setDone(task, !task.done) }
    }

    fun delete(task: StudyTaskEntity) {
        viewModelScope.launch { tasksRepository.deleteTask(task) }
    }

    fun sync() {
        _uiState.update { it.copy(syncing = true, offline = false) }
        viewModelScope.launch {
            when (val outcome = tasksRepository.sync()) {
                is com.campusute.app.core.data.SyncRunOutcome.Success -> _uiState.update {
                    it.copy(
                        syncing = false,
                        offline = false,
                        conflicts = outcome.conflicts,
                        pending = tasksRepository.pendingCount(),
                    )
                }
                com.campusute.app.core.data.SyncRunOutcome.Offline -> _uiState.update {
                    it.copy(syncing = false, offline = true)
                }
                is com.campusute.app.core.data.SyncRunOutcome.Failure -> _uiState.update {
                    it.copy(syncing = false, offline = true)
                }
            }
        }
    }
}
