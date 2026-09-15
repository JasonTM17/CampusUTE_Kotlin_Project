package com.campusute.app.core.gpa

/**
 * Deterministic 4.0-scale GPA math (HCMUTE 10→4 conversion). Pure Kotlin —
 * the LLM must NEVER compute grades (plan ADR: deterministic boundaries).
 */
object GpaCalculator {

    /** 10-point score -> 4.0 letter point (HCMUTE academic regulation table). */
    fun gradePoint(score10: Double): Double = when {
        score10 >= 9.0 -> 4.0
        score10 >= 8.0 -> 3.5
        score10 >= 7.0 -> 3.0
        score10 >= 6.0 -> 2.0
        score10 >= 5.0 -> 1.5
        else -> 0.0
    }

    fun letter(score10: Double): String = when {
        score10 >= 8.5 -> "A"
        score10 >= 8.0 -> "B+"
        score10 >= 7.0 -> "B"
        score10 >= 6.5 -> "C+"
        score10 >= 5.5 -> "C"
        score10 >= 5.0 -> "D+"
        score10 >= 4.0 -> "D"
        else -> "F"
    }

    data class CourseScore(val credits: Int, val score10: Double)

    /** Credit-weighted GPA over completed courses. */
    fun gpa(courses: List<CourseScore>): Double {
        val totalCredits = courses.sumOf { it.credits }
        if (totalCredits == 0) return 0.0
        return courses.sumOf { it.credits * gradePoint(it.score10) } / totalCredits
    }

    /**
     * What-if: projected course total from component scores; missing FINAL is
     * solved from the target (invert the weighted sum) when solvable.
     */
    data class Component(val name: String, val score: Double, val weight: Double)

    fun courseTotal(components: List<Component>): Double =
        components.sumOf { it.score * it.weight }

    /** Required FINAL score to reach [target] given the other components. */
    fun requiredFinal(
        target: Double,
        others: List<Component>,
        finalWeight: Double,
    ): Double? {
        if (finalWeight <= 0.0) return null
        val earned = others.sumOf { it.score * it.weight }
        val needed = (target - earned) / finalWeight
        return needed.coerceIn(0.0, 10.0)
    }
}
