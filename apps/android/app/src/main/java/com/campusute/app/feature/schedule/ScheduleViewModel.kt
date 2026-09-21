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
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val daySessions: List<ScheduleSessionEntity> = emptyList(),
    val weekSessions: List<ScheduleSessionEntity> = emptyList(),
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
) {
    /** A week is "empty" only when all seven days are — one blank Tuesday is not an empty week. */
    val weekIsEmpty: Boolean get() = !loading && weekSessions.isEmpty()
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        observeWeek(_uiState.value.weekStart)
        selectDay(LocalDate.now())
    }

    private var dayJob: kotlinx.coroutines.Job? = null
    private var weekJob: kotlinx.coroutines.Job? = null

    fun selectDay(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, loading = true) }
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            scheduleRepository.observeDay(date.iso()).collect { sessions ->
                _uiState.update { state -> state.copy(daySessions = sessions, loading = false) }
            }
        }
    }

    /** Re-observes the whole visible week whenever the anchor moves. */
    private fun observeWeek(weekStart: LocalDate) {
        weekJob?.cancel()
        weekJob = viewModelScope.launch {
            scheduleRepository.observeWeek(weekStart.iso(), weekStart.plusDays(6).iso()).collect { sessions ->
                _uiState.update { it.copy(weekSessions = sessions) }
            }
        }
    }

    fun refresh() {
        val week = _uiState.value.weekStart
        _uiState.update { it.copy(syncing = true, message = null, offline = false) }
        viewModelScope.launch {
            when (val outcome = scheduleRepository.sync(week.iso(), week.plusDays(6).iso())) {
                is ScheduleSyncOutcome.Success -> _uiState.update {
                    it.copy(syncing = false, offline = false)
                }
                ScheduleSyncOutcome.Offline -> _uiState.update {
                    it.copy(syncing = false, offline = true)
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
        observeWeek(newStart)
        // Landing on Monday keeps the strip selection inside the week that is now on screen.
        selectDay(if (_uiState.value.selectedDate in newStart..newStart.plusDays(6)) _uiState.value.selectedDate else newStart)
        refresh()
    }

    private fun LocalDate.iso(): String = format(iso)
}
