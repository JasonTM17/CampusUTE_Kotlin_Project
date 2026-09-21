package com.campusute.app.feature.grades

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.CourseGradesDto
import com.campusute.app.core.network.GradeComponentDto
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.httpRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Gate for catalogue entry #3. Two properties matter more than the layout: a course whose weights
 * do not add up must not be graded, and no cumulative GPA may appear anywhere, because
 * `grades/me` returns no credit counts and [com.campusute.app.core.gpa.GpaCalculator] needs them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradesScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    private class GradesApi(
        var payload: ApiEnvelopeDto<List<CourseGradesDto>> = ApiEnvelopeDto(data = emptyList()),
        var failure: Throwable? = null,
    ) : StubCampusApi() {
        override suspend fun gradesMe(): ApiEnvelopeDto<List<CourseGradesDto>> {
            failure?.let { throw it }
            return payload
        }
    }

    private fun show(api: GradesApi) {
        composeRule.setContent {
            MaterialTheme { GradesScreen(GradesViewModel(api)) }
        }
        composeRule.waitForIdle()
    }

    private fun course(
        code: String,
        name: String,
        components: List<Pair<String, Pair<Double, Double>>>,
    ) = CourseGradesDto(
        courseId = code,
        courseCode = code,
        courseName = name,
        components = components.map { (component, pair) ->
            GradeComponentDto(component, pair.first, pair.second)
        },
    )

    @Test
    fun `a refused grades call is a failure, not an empty transcript`() {
        show(GradesApi(failure = httpRefusal(500, "Lỗi máy chủ")))
        // The server's own words, not a generic fallback.
        composeRule.onNodeWithText("Lỗi máy chủ").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
        composeRule.onNodeWithText("Chưa có điểm nào").assertDoesNotExist()
    }

    @Test
    fun `an envelope error is a failure too`() {
        show(GradesApi(payload = ApiEnvelopeDto(error = ApiErrorDto("AUTH_FORBIDDEN", "Không truy cập được"))))
        composeRule.onNodeWithText("Không truy cập được").assertExists()
        composeRule.onNodeWithText("Chưa có điểm nào").assertDoesNotExist()
    }

    @Test
    fun `offline is its own diagnosis`() {
        show(GradesApi(failure = IOException("no route")))
        composeRule.onNodeWithText("Không kết nối được máy chủ.").assertExists()
    }

    @Test
    fun `a healthy empty transcript reads as an honest zero`() {
        show(GradesApi())
        composeRule.onNodeWithText("Chưa có điểm nào").assertExists()
    }

    @Test
    fun `a complete course shows weighted components and a letter`() {
        show(
            GradesApi(
                payload = ApiEnvelopeDto(
                    data = listOf(
                        course(
                            "DBMS311",
                            "Hệ quản trị cơ sở dữ liệu",
                            listOf(
                                "MIDTERM" to (7.5 to 0.3),
                                "ASSIGNMENT" to (8.5 to 0.2),
                                "FINAL" to (9.0 to 0.5),
                            ),
                        ),
                    ),
                ),
            ),
        )
        // 7.5*0.3 + 8.5*0.2 + 9.0*0.5 = 2.25 + 1.7 + 4.5 = 8.45 -> B on the HCMUTE scale.
        composeRule.onNodeWithText("B · 8.45").assertExists()
        composeRule.onNodeWithText("Giữa kỳ").assertExists()
        composeRule.onNodeWithText("7.5 · 30%").assertExists()
        composeRule.onNodeWithText("Cuối kỳ").assertExists()
    }

    @Test
    fun `a failing course is styled as failing`() {
        show(
            GradesApi(
                payload = ApiEnvelopeDto(
                    data = listOf(
                        course(
                            "CALC201",
                            "Giải tích 2",
                            listOf("FINAL" to (3.0 to 1.0)),
                        ),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithText("F · 3").assertExists()
    }

    @Test
    fun `weights that do not add up refuse to print a grade`() {
        show(
            GradesApi(
                payload = ApiEnvelopeDto(
                    data = listOf(
                        course(
                            "SE104",
                            "Thực tập",
                            listOf("MIDTERM" to (9.0 to 0.3)),
                        ),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithText("Chưa đủ trọng số").assertExists()
        // A 9.0 at 30% weight is not a 9.0 course; printing "A" here would be a fabrication.
        composeRule.onNodeWithText("A · 2.7").assertDoesNotExist()
    }

    @Test
    fun `no cumulative gpa is claimed anywhere`() {
        show(
            GradesApi(
                payload = ApiEnvelopeDto(
                    data = listOf(
                        course("DBMS311", "Hệ quản trị cơ sở dữ liệu", listOf("FINAL" to (9.0 to 1.0))),
                        course("CALC201", "Giải tích 2", listOf("FINAL" to (6.0 to 1.0))),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithText("GPA", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Điểm trung bình tích lũy", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("ĐTB", substring = true).assertDoesNotExist()
    }

    @Test
    fun `retry reloads after the outage clears`() {
        val api = GradesApi(failure = httpRefusal(500, "Lỗi máy chủ"))
        show(api)
        api.failure = null
        api.payload = ApiEnvelopeDto(
            data = listOf(course("ENG101", "Tiếng Anh 1", listOf("FINAL" to (8.0 to 1.0)))),
        )
        composeRule.onNodeWithText("Thử lại").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("B · 8").assertExists()
        composeRule.onNodeWithText("Không tải được bảng điểm.").assertDoesNotExist()
    }
}
