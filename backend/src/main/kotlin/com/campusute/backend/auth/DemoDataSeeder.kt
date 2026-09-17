package com.campusute.backend.auth

import com.campusute.backend.academic.ClassSectionRepository
import com.campusute.backend.academic.CourseRepository
import com.campusute.backend.academic.Enrollment
import com.campusute.backend.academic.EnrollmentRepository
import com.campusute.backend.academic.GradeRepository
import com.campusute.backend.audit.AuditService
import com.campusute.backend.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Seeds synthetic demo identities when app.demo-mode=true (dev only — the
 * production profile disables it). All credentials below are FAKE and exist
 * solely so the app is demo-ready without real HCMUTE data.
 */
@Component
class DemoDataSeeder(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val props: AppProperties,
    private val audit: AuditService,
    private val sections: ClassSectionRepository,
    private val enrollments: EnrollmentRepository,
    private val courses: CourseRepository,
    private val grades: GradeRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DemoDataSeeder::class.java)

    data class DemoAccount(val email: String, val password: String, val role: String, val name: String, val studentCode: String? = null)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!props.demoMode) return

        val accounts = listOf(
            DemoAccount("student@demo.campusute.vn", "Demo#Student1", "STUDENT", "Nguyễn Văn Sơn", "21110101"),
            DemoAccount("lecturer@demo.campusute.vn", "Demo#Lecturer1", "LECTURER", "Trần Thị Bích", null),
            DemoAccount("admin@demo.campusute.vn", "Demo#Admin1", "ADMIN", "System Administrator", null),
        )

        for (account in accounts) {
            if (users.findByEmailIgnoreCase(account.email) != null) continue
            val role = roles.findByName(account.role) ?: continue
            users.save(
                User(
                    email = account.email,
                    passwordHash = passwordEncoder.encode(account.password),
                    fullName = account.name,
                    studentCode = account.studentCode,
                    department = if (account.role == "STUDENT") "Công nghệ Thông tin" else null,
                    roles = mutableSetOf(role),
                ),
            )
            log.info("seeded demo account: {} ({})", account.email, account.role)
        }
        // Flyway seed runs before demo users exist, so the demo student's
        // enrollments are (re)created here — idempotent, fresh-DB safe.
        users.findByEmailIgnoreCase("student@demo.campusute.vn")?.let { student ->
            val studentId = requireNotNull(student.id)
            if (enrollments.findByStudentId(studentId).isEmpty()) {
                enrollments.saveAll(
                    sections.findAll().map { section -> Enrollment(studentId = studentId, classSectionId = section.id) },
                )
                log.info("seeded demo enrollments for {}", student.email)
            }
            seedDemoGrades(studentId)
        }
        audit.record(null, "SEED_DEMO", "users")
    }

    /** V4 grade inserts cannot see demo users at migration time — upsert here. */
    private fun seedDemoGrades(studentId: java.util.UUID) {
        val course = courses.findByCode("DBMS311") ?: return
        val existing = grades.findByStudentId(studentId)
        val wanted = listOf(
            Triple("MIDTERM", 7.5, 0.3),
            Triple("ASSIGNMENT", 8.5, 0.2),
            Triple("FINAL", 0.0, 0.5),
        )
        for ((component, score, weight) in wanted) {
            if (existing.none { it.courseId == course.id && it.component == component }) {
                grades.save(
                    com.campusute.backend.academic.Grade(
                        studentId = studentId,
                        courseId = course.id,
                        component = component,
                        score = java.math.BigDecimal(score),
                        weight = java.math.BigDecimal(weight),
                    ),
                )
            }
        }
    }
}
