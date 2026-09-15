package com.campusute.app.core.gpa

import org.junit.Assert.assertEquals
import org.junit.Test

/** Deterministic truth table for the GPA engine (plan Phase 4 gate). */
class GpaCalculatorTest {

    @Test
    fun `grade point thresholds match the regulation table`() {
        val table = mapOf(
            10.0 to 4.0, 9.0 to 4.0, 8.9 to 3.5, 8.0 to 3.5,
            7.9 to 3.0, 7.0 to 3.0, 6.9 to 2.0, 6.0 to 2.0,
            5.9 to 1.5, 5.0 to 1.5, 4.9 to 0.0, 0.0 to 0.0,
        )
        table.forEach { (score, expected) ->
            assertEquals("score=$score", expected, GpaCalculator.gradePoint(score), 1e-9)
        }
    }

    @Test
    fun `letters round at regulation cut-offs`() {
        assertEquals("A", GpaCalculator.letter(8.5))
        assertEquals("B+", GpaCalculator.letter(8.0))
        assertEquals("B", GpaCalculator.letter(7.0))
        assertEquals("F", GpaCalculator.letter(4.0))
        assertEquals("D+", GpaCalculator.letter(5.0))
    }

    @Test
    fun `gpa is credit weighted`() {
        val gpa = GpaCalculator.gpa(
            listOf(
                GpaCalculator.CourseScore(credits = 3, score10 = 8.0), // 3.5
                GpaCalculator.CourseScore(credits = 2, score10 = 5.0), // 1.5
            ),
        )
        assertEquals((3 * 3.5 + 2 * 1.5) / 5, gpa, 1e-9)
    }

    @Test
    fun `gpa of empty list is zero`() {
        assertEquals(0.0, GpaCalculator.gpa(emptyList()), 1e-9)
    }

    @Test
    fun `required final solves the weighted equation`() {
        // components: assignment 8.5*0.2 + midterm 7.5*0.3 = 3.95; target 8.0 with final 0.5
        val needed = GpaCalculator.requiredFinal(
            target = 8.0,
            others = listOf(
                GpaCalculator.Component("ASSIGNMENT", 8.5, 0.2),
                GpaCalculator.Component("MIDTERM", 7.5, 0.3),
            ),
            finalWeight = 0.5,
        )
        assertEquals(8.1, needed!!, 1e-9)
    }

    @Test
    fun `impossible target clamps to ten`() {
        val needed = GpaCalculator.requiredFinal(
            target = 10.0,
            others = listOf(GpaCalculator.Component("ASSIGNMENT", 2.0, 0.2)),
            finalWeight = 0.5,
        )
        assertEquals(10.0, needed!!, 1e-9)
    }
}
