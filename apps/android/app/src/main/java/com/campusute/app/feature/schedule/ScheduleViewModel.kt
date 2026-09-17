package com.campusute.app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.ScheduleRepository
import com.campusute.app.core.data.ScheduleSyncOutcome
import com.campusute.app.core.database.ScheduleSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TimetableUiState(
    val weekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val selectedDate: LocalDate = LocalDate.now(),
    val daySessions: List<ScheduleSessionEntity> = emptyList(),
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
    val hasConflict: Boolean = false,
    val empty: Boolean = false,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        selectDay(LocalDate.now())
    }

    private var dayJob: kotlinx.coroutines.Job? = null

    fun selectDay(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, loading = true, empty = false) }
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            scheduleRepository.observeDay(date.iso()).collect { sessions ->
                _uiState.update { state ->
                    state.copy(
                        daySessions = sessions,
                        loading = false,
                        empty = sessions.isEmpty(),
                    )
                }
            }
        }
    }

    fun refresh() {
        val week = _uiState.value.weekStart
        _uiState.update { it.copy(syncing = true, message = null, offline = false) }
        viewModelScope.launch {
            when (val outcome = scheduleRepository.sync(week.iso(), week.plusDays(6).iso())) {
                is ScheduleSyncOutcome.Success -> _uiState.update {
                    it.copy(syncing = false, hasConflict = outcome.conflict, offline = false)
                }
                ScheduleSyncOutcome.Offline -> _uiState.update {
                    it.copy(syncing = false, offline = true, message = "Ngoại tuyến — đang hiển thị dữ liệu đã lưu")
                }
                is ScheduleSyncOutcome.Failure -> _uiState.update {
                    it.copy(syncing = false, message = outcome.message)
                }
            }
        }
    }

    fun changeWeek(forward: Boolean) {
        val newStart = _uiState.value.weekStart.plusWeeks(if (forward) 1 else -1)
        _uiState.update { it.copy(weekStart = newStart) }
        selectDay(newStart)
        refresh()
    }

    private fun LocalDate.iso(): String = format(iso)
}
