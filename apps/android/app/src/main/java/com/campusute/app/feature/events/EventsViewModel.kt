package com.campusute.app.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.CampusEventDto
import com.campusute.app.core.network.envelopeError
import com.campusute.app.feature.appshell.LoadPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID

sealed interface RegisterOutcome {
    val eventId: String

    data class Registered(override val eventId: String) : RegisterOutcome
    data class Rejected(override val eventId: String, val message: String) : RegisterOutcome
}

data class EventsUiState(
    val events: List<CampusEventDto> = emptyList(),
    val phase: LoadPhase = LoadPhase.Loading,
    val message: String? = null,
    val registeringId: String? = null,
    val outcome: RegisterOutcome? = null,
)

/**
 * Sự kiện (catalogue #9). Registration is idempotent server-side on (student, Idempotency-Key),
 * and the backend rejects a blank or over-long key — so one key is minted per *intent* and reused
 * for every retry of that intent. A double tap therefore cannot take two seats, and a lost
 * response can be re-sent safely.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val api: CampusApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private val keys = mutableMapOf<String, String>()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(phase = LoadPhase.Loading, message = null) }
        viewModelScope.launch {
            try {
                val envelope = api.events()
                val error = envelope.error
                val data = envelope.data
                when {
                    error != null -> _uiState.update {
                        it.copy(phase = LoadPhase.Failed, message = error.message)
                    }
                    data == null -> _uiState.update {
                        it.copy(phase = LoadPhase.Failed, message = "Phản hồi không có dữ liệu.")
                    }
                    else -> _uiState.update {
                        it.copy(events = data.sortedBy { event -> event.startsAt }, phase = LoadPhase.Ready)
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(phase = LoadPhase.Failed, message = "Không kết nối được máy chủ.")
                }
            } catch (http: HttpException) {
                _uiState.update {
                    it.copy(
                        phase = LoadPhase.Failed,
                        message = http.envelopeError()?.message ?: "Không tải được sự kiện.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(phase = LoadPhase.Failed, message = "Không tải được sự kiện.") }
            }
        }
    }

    fun register(event: CampusEventDto) {
        if (_uiState.value.registeringId != null) return
        // Guard on the row this ViewModel is actually holding, not on whatever copy the caller
        // passed: a stale argument must not be able to book a second seat for a registered event.
        val current = _uiState.value.events.firstOrNull { it.id == event.id } ?: event
        if (current.registered || current.seatsLeft <= 0L || isPast(current)) return
        val key = keys.getOrPut(current.id) { UUID.randomUUID().toString() }
        _uiState.update { it.copy(registeringId = current.id, outcome = null) }
        viewModelScope.launch {
            val result = try {
                val envelope = api.registerEvent(current.id, key)
                val error = envelope.error
                if (error != null) {
                    RegisterOutcome.Rejected(current.id, error.message)
                } else {
                    RegisterOutcome.Registered(current.id)
                }
            } catch (_: IOException) {
                RegisterOutcome.Rejected(current.id, "Không có kết nối — chưa ghi danh.")
            } catch (http: HttpException) {
                RegisterOutcome.Rejected(current.id, http.envelopeError()?.message ?: "Không ghi danh được.")
            } catch (_: Exception) {
                RegisterOutcome.Rejected(current.id, "Không ghi danh được.")
            }
            if (result is RegisterOutcome.Registered) {
                // The key is spent: a later, separate intent must not silently re-join the old
                // idempotent request and get a cached answer instead of a new seat.
                keys.remove(current.id)
            }
            _uiState.update { state ->
                state.copy(
                    registeringId = null,
                    outcome = result,
                    events = if (result is RegisterOutcome.Registered) {
                        state.events.map {
                            if (it.id == current.id) {
                                it.copy(registered = true, seatsLeft = (it.seatsLeft - 1).coerceAtLeast(0))
                            } else {
                                it
                            }
                        }
                    } else {
                        state.events
                    },
                )
            }
        }
    }

    fun clearOutcome() = _uiState.update { it.copy(outcome = null) }
}

fun isPast(event: CampusEventDto, now: Instant = Instant.now()): Boolean {
    val starts = runCatching { Instant.parse(event.startsAt) }.getOrNull() ?: return false
    return now.isAfter(starts)
}
