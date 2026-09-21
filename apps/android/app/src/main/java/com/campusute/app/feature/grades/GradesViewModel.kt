package com.campusute.app.feature.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.gpa.GpaCalculator
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.CourseGradesDto
import com.campusute.app.core.network.envelopeMessage
import com.campusute.app.feature.appshell.LoadPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/** One component line: "Giữa kỳ · 7.5 · trọng số 30%". */
data class ComponentRow(val label: String, val score: Double, val weightPercent: Int)

data class CourseGrade(
    val courseCode: String,
    val courseName: String,
    val components: List<ComponentRow>,
    /** Null when the delivered weights do not add up — see [GradesViewModel]. */
    val total: Double?,
    val letter: String?,
) {
    val isFailing: Boolean get() = total != null && total < PASSING_TOTAL
    val weightSumPercent: Int get() = components.sumOf { it.weightPercent }
}

const val PASSING_TOTAL = 5.0

data class GradesUiState(
    val courses: List<CourseGrade> = emptyList(),
    val phase: LoadPhase = LoadPhase.Loading,
    val message: String? = null,
)

/**
 * Điểm & học phần (catalogue #3).
 *
 * Deliberately per-course only. `grades/me` returns components and weights but no credit counts,
 * and a credit-weighted GPA needs those counts — so the cumulative number is not computable from
 * what the client is given, and this screen does not show one. The 10→4 letter scale is the HCMUTE
 * mapping already owned by [GpaCalculator] and is not restated here.
 *
 * A total is only claimed when the delivered weights sum to a whole course. The payload has no
 * "not yet graded" marker, and a seeded FINAL of 0.0 is indistinguishable from an unentered one,
 * so guessing would print an F over a student who simply has not sat the exam yet.
 */
@HiltViewModel
class GradesViewModel @Inject constructor(
    private val api: CampusApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(phase = LoadPhase.Loading, message = null) }
        viewModelScope.launch {
            try {
                val envelope = api.gradesMe()
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
                        it.copy(courses = data.map(::toCourseGrade), phase = LoadPhase.Ready)
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
                        message = http.envelopeMessage() ?: "Không tải được bảng điểm.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(phase = LoadPhase.Failed, message = "Không tải được bảng điểm.")
                }
            }
        }
    }

    private fun toCourseGrade(dto: CourseGradesDto): CourseGrade {
        val rows = dto.components.map {
            ComponentRow(componentLabel(it.component), it.score, (it.weight * 100).roundToInt())
        }
        val weightSum = dto.components.sumOf { it.weight }
        val complete = dto.components.isNotEmpty() && weightSum >= COMPLETE_WEIGHT
        val total = if (complete) {
            GpaCalculator.courseTotal(
                dto.components.map { GpaCalculator.Component(it.component, it.score, it.weight) },
            )
        } else {
            null
        }
        return CourseGrade(
            courseCode = dto.courseCode,
            courseName = dto.courseName,
            components = rows,
            total = total,
            letter = total?.let { GpaCalculator.letter(it) },
        )
    }

    private fun componentLabel(component: String): String = when (component) {
        "MIDTERM" -> "Giữa kỳ"
        "FINAL" -> "Cuối kỳ"
        "ASSIGNMENT" -> "Bài tập"
        else -> component.lowercase()
    }

    private companion object {
        /** Tolerance for float weights that are meant to total 1.0. */
        const val COMPLETE_WEIGHT = 0.999
    }
}
