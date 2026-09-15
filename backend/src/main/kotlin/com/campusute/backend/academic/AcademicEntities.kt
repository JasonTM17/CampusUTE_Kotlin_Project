package com.campusute.backend.academic

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "courses")
class Course(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) val code: String,
    @Column(nullable = false) val name: String,
    val credits: Int = 3,
)

@Entity
@Table(name = "lecturers")
class Lecturer(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: UUID? = null,
    @Column(name = "full_name", nullable = false) val fullName: String,
    val department: String? = null,
)

@Entity
@Table(name = "class_sections")
class ClassSection(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "course_id", nullable = false) val courseId: UUID,
    @Column(name = "lecturer_id", nullable = false) val lecturerId: UUID,
    @Column(name = "section_code", nullable = false, unique = true) val sectionCode: String,
    val semester: String,
    val building: String,
    val room: String,
)

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["student_id", "class_section_id"])],
)
class Enrollment(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "student_id", nullable = false) val studentId: UUID,
    @Column(name = "class_section_id", nullable = false) val classSectionId: UUID,
)

@Entity
@Table(name = "schedule_sessions")
class ScheduleSession(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "class_section_id", nullable = false) val classSectionId: UUID,
    @Column(name = "day_of_week", nullable = false) val dayOfWeek: Short,
    @Column(name = "start_time", nullable = false) val startTime: LocalTime,
    @Column(name = "end_time", nullable = false) val endTime: LocalTime,
    @Column(name = "start_date", nullable = false) val startDate: LocalDate,
    @Column(name = "end_date", nullable = false) val endDate: LocalDate,
)

interface CourseRepository : JpaRepository<Course, UUID>
interface LecturerRepository : JpaRepository<Lecturer, UUID>
interface ClassSectionRepository : JpaRepository<ClassSection, UUID>
interface ScheduleSessionRepository : JpaRepository<ScheduleSession, UUID> {
    fun findByClassSectionIdInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        sectionIds: Collection<UUID>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ScheduleSession>
}

interface EnrollmentRepository : JpaRepository<Enrollment, UUID> {
    @Query("SELECT e.classSectionId FROM Enrollment e WHERE e.studentId = :studentId")
    fun sectionIdsOfStudent(@Param("studentId") studentId: UUID): List<UUID>

    fun findByStudentId(studentId: UUID): List<Enrollment>
}
