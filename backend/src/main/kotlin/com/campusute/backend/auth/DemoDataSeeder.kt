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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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
    private val jdbc: JdbcTemplate,
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
            seedDemoNotifications(studentId)
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

    private data class DemoNotification(
        val key: String,
        val type: String,
        val title: String,
        val body: String,
        val hoursAgo: Long,
        val read: Boolean,
    )

    /** Synthetic inbox content for the demo student; keys stay stable across restarts. */
    private fun seedDemoNotifications(studentId: UUID) {
        val seedTime = Instant.now()
        val demoNotifications = listOf(
            DemoNotification("grade-dbms-midterm", "GRADE", "DBMS311 · Điểm giữa kỳ đã cập nhật", "Điểm minh họa: 7,5/10. Bạn có thể mở mục Điểm số để xem lại thành phần đánh giá và ghi chú của học phần.", 2, true),
            DemoNotification("grade-dbms-assignment", "GRADE", "DBMS311 · Điểm bài tập đã cập nhật", "Điểm minh họa: 8,5/10. Hãy kiểm tra phần nhận xét để biết những mục đã hoàn thành tốt và nội dung nên rà soát thêm.", 8, true),
            DemoNotification("grade-dbms-weighting", "GRADE", "DBMS311 · Trọng số các cột điểm", "Bảng điểm minh họa đang chia thành phần giữa kỳ 30%, bài tập 20% và cuối kỳ 50%. Tổng trọng số là 100%.", 21, false),
            DemoNotification("grade-se104-feedback", "GRADE", "SE104 · Có nhận xét cho bài tập nhóm", "Mẫu nhận xét đã sẵn sàng trong trang điểm: rà soát tính đầy đủ của yêu cầu, sơ đồ luồng chính và phần phân công công việc.", 38, true),
            DemoNotification("grade-net325-quiz", "GRADE", "NET325 · Kết quả bài kiểm tra ngắn", "Đây là thông báo minh họa về kết quả kiểm tra. Mở chi tiết học phần để xem điểm từng phần và đối chiếu với nội dung đã ôn tập.", 62, true),
            DemoNotification("grade-os321-lab", "GRADE", "OS321 · Đã ghi nhận điểm thực hành", "Điểm thực hành mẫu đã được ghi vào bảng điểm. Bạn nên xem lại tiêu chí về tiến trình, xử lý trạng thái và phần giải thích kết quả.", 95, false),
            DemoNotification("grade-ai410-project", "GRADE", "AI410 · Cập nhật đánh giá đồ án", "Bản demo đã thêm một mốc đánh giá cho đồ án. Hãy đối chiếu mục tiêu, dữ liệu đầu vào và cách trình bày kết quả trong phần phản hồi.", 130, true),
            DemoNotification("grade-dbms-review", "GRADE", "DBMS311 · Hướng dẫn đề nghị xem lại điểm", "Nếu cần trao đổi về một mục điểm, hãy ghi rõ thành phần, câu hỏi và căn cứ trong bài làm trước khi gửi yêu cầu xem lại.", 170, true),
            DemoNotification("grade-se104-components", "GRADE", "SE104 · Bảng điểm theo thành phần", "Bản minh họa nhóm các cột điểm theo bài tập, kiểm tra và đồ án để bạn dễ theo dõi tiến độ đánh giá của học phần.", 220, true),
            DemoNotification("grade-net325-rubric", "GRADE", "NET325 · Nhắc xem tiêu chí chấm", "Trước khi đối chiếu điểm, hãy mở rubric và kiểm tra các mục cấu hình, giải thích kết quả, trình bày sơ đồ và nguồn tham khảo.", 280, false),
            DemoNotification("grade-os321-progress", "GRADE", "OS321 · Tổng quan tiến độ học tập", "Thông báo mẫu tổng hợp các cột điểm đã có và còn thiếu. Dữ liệu điểm trong môi trường demo chỉ phục vụ trình diễn giao diện.", 360, true),
            DemoNotification("grade-ai410-record", "GRADE", "AI410 · Mẹo kiểm tra bảng điểm", "Kiểm tra đúng học phần và học kỳ, sau đó so sánh từng cột với tiêu chí đánh giá. Các dữ liệu hiển thị trong tài khoản demo là dữ liệu mô phỏng.", 500, true),

            DemoNotification("assignment-dbms-schema", "ASSIGNMENT", "DBMS311 · Thiết kế lược đồ dữ liệu", "Bài tập mẫu: mô hình hóa các thực thể, khóa và quan hệ cho một thư viện nhỏ. Đính kèm sơ đồ cùng một đoạn giải thích các ràng buộc chính.", 4, false),
            DemoNotification("assignment-ai410-prototype", "ASSIGNMENT", "AI410 · Nộp bản thử nghiệm mô hình", "Chuẩn bị một bản thử nghiệm ngắn: mô tả dữ liệu, tiêu chí đánh giá và một ví dụ đầu vào/đầu ra. Không đưa thông tin cá nhân vào dữ liệu mẫu.", 12, false),
            DemoNotification("assignment-se104-requirements", "ASSIGNMENT", "SE104 · Hoàn thiện đặc tả yêu cầu", "Bài tập minh họa gồm mục tiêu, tác nhân, luồng chính, ngoại lệ và tiêu chí chấp nhận. Hãy giữ tên vai trò nhất quán giữa đặc tả và sơ đồ.", 28, true),
            DemoNotification("assignment-net325-lab", "ASSIGNMENT", "NET325 · Thực hành cấu hình mạng", "Mẫu thực hành yêu cầu mô tả sơ đồ kết nối, bảng địa chỉ, các bước cấu hình và kết quả kiểm tra kết nối giữa những thiết bị.", 47, true),
            DemoNotification("assignment-os321-scheduler", "ASSIGNMENT", "OS321 · Mô phỏng lập lịch tiến trình", "Bài tập mẫu: chạy hai thuật toán lập lịch trên cùng bộ tiến trình, ghi thời gian chờ và thời gian hoàn thành, rồi giải thích khác biệt.", 72, false),
            DemoNotification("assignment-dbms-normalization", "ASSIGNMENT", "DBMS311 · Ôn tập chuẩn hóa quan hệ", "Hãy xác định phụ thuộc hàm, tìm khóa ứng viên và tách quan hệ đến dạng chuẩn phù hợp. Nêu rõ vì sao phép tách bảo toàn thông tin.", 105, false),
            DemoNotification("assignment-ai410-reading", "ASSIGNMENT", "AI410 · Tài liệu chuẩn bị buổi học", "Danh sách đọc mẫu gồm phần tổng quan bài toán, mô tả bộ dữ liệu và cách đo chất lượng. Ghi lại một câu hỏi để trao đổi trong giờ học.", 145, true),
            DemoNotification("assignment-se104-review", "ASSIGNMENT", "SE104 · Chuẩn bị phiên rà soát thiết kế", "Trước phiên rà soát, hãy kiểm tra sơ đồ có đủ luồng thay thế, trạng thái lỗi và liên kết giữa yêu cầu với ca sử dụng hay chưa.", 190, false),
            DemoNotification("assignment-os321-lab-notes", "ASSIGNMENT", "OS321 · Bổ sung nhật ký thực hành", "Nhật ký mẫu nên ghi môi trường chạy, cấu hình đầu vào, thao tác đã thử và kết quả quan sát được để người khác có thể lặp lại.", 245, true),
            DemoNotification("assignment-net325-report", "ASSIGNMENT", "NET325 · Hoàn thiện báo cáo thí nghiệm", "Bản báo cáo minh họa gồm mục tiêu, sơ đồ, lệnh kiểm tra, kết quả và nhận xét. Che mọi thông tin định danh nếu dùng ảnh chụp thật.", 315, false),
            DemoNotification("assignment-dbms-submit", "ASSIGNMENT", "DBMS311 · Xác nhận bản nộp mẫu", "Một bản nộp thử đã được ghi nhận trong luồng demo. Bạn có thể dùng mục Công việc để xem trạng thái và kiểm tra tệp đính kèm.", 410, false),
            DemoNotification("assignment-ai410-checklist", "ASSIGNMENT", "AI410 · Checklist trước khi nộp", "Rà soát mô tả mục tiêu, nguồn dữ liệu, cách đánh giá, giới hạn của kết quả và hướng dẫn chạy lại trước khi hoàn tất bài tập.", 600, true),

            DemoNotification("system-welcome", "SYSTEM", "Chào mừng đến hộp thư CampusUTE", "Đây là nội dung minh họa cho tài khoản demo. Hộp thư gom cập nhật điểm, bài tập và thông báo hệ thống vào một nơi.", 1, true),
            DemoNotification("system-maintenance", "SYSTEM", "Hệ thống demo · Lịch bảo trì mẫu", "Thông báo bảo trì này chỉ dùng để minh họa trạng thái hệ thống. Trong môi trường thật, thời gian ảnh hưởng sẽ được công bố kèm hướng dẫn cụ thể.", 15, false),
            DemoNotification("system-navigation", "SYSTEM", "Mẹo sử dụng các bộ lọc thông báo", "Chọn Tất cả, Chưa đọc, Điểm số, Bài tập hoặc Hệ thống để thu hẹp danh sách. Chạm một mục chưa đọc để chuyển mục đó sang trạng thái đã đọc.", 32, true),
            DemoNotification("system-account-safety", "SYSTEM", "Nhắc nhở an toàn tài khoản", "Không chia sẻ mật khẩu hoặc mã xác thực. Khi dùng thiết bị chung, hãy đăng xuất sau phiên làm việc và tránh lưu thông tin đăng nhập.", 53, true),
            DemoNotification("system-profile", "SYSTEM", "Kiểm tra thông tin hồ sơ demo", "Hãy xem lại các mục hồ sơ và thông tin liên hệ mẫu để làm quen với giao diện. Tài khoản demo không đại diện cho hồ sơ sinh viên thật.", 82, false),
            DemoNotification("system-privacy", "SYSTEM", "Lưu ý về dữ liệu minh họa", "Tên, nội dung bài tập và trạng thái trong tài khoản này được tạo cho mục đích trình diễn; không dùng chúng làm căn cứ học vụ.", 118, true),
            DemoNotification("system-sync", "SYSTEM", "Mẹo làm mới dữ liệu", "Kéo danh sách xuống để tải lại dữ liệu từ máy chủ. Nếu mạng gián đoạn, kiểm tra kết nối rồi thử làm mới thêm lần nữa.", 155, false),
            DemoNotification("system-help", "SYSTEM", "Chuẩn bị yêu cầu hỗ trợ", "Khi cần hỗ trợ, mô tả màn hình, thao tác trước khi gặp lỗi và thời điểm xảy ra. Không gửi mật khẩu hoặc thông tin cá nhân nhạy cảm.", 205, true),
            DemoNotification("system-read-state", "SYSTEM", "Quản lý trạng thái đã đọc", "Mục chưa đọc được gom riêng để dễ theo dõi. Bạn có thể mở từng thông báo; trạng thái sẽ đồng bộ lại sau khi máy chủ xác nhận.", 260, true),
            DemoNotification("system-connection", "SYSTEM", "Trạng thái kết nối trong bản demo", "Nội dung này minh họa thông báo hệ thống khi ứng dụng cần kết nối lại. Hãy kiểm tra Wi-Fi hoặc dữ liệu di động nếu danh sách chưa cập nhật.", 335, false),
            DemoNotification("system-release-notes", "SYSTEM", "Thông tin cập nhật ứng dụng", "Bản demo giới thiệu các khu vực hồ sơ, lịch học, công việc và hộp thư. Một số thao tác minh họa có thể không kết nối dịch vụ học vụ thật.", 450, true),
            DemoNotification("system-feedback", "SYSTEM", "Cảm ơn bạn đã trải nghiệm", "Bạn có thể dùng tài khoản demo để xem cách nhóm thông báo, mở chi tiết và kiểm tra trạng thái chưa đọc. Cảm ơn bạn đã góp ý cho giao diện.", 680, true),
        )
        val sql = """
            INSERT INTO notifications (id, user_id, type, title, body, read, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()

        var insertedCount = 0
        for (notification in demoNotifications) {
            val id = UUID.nameUUIDFromBytes(
                "campusute:demo-notification:v1:${notification.key}".toByteArray(StandardCharsets.UTF_8),
            )
            insertedCount += jdbc.update(
                sql,
                id,
                studentId,
                notification.type,
                notification.title.take(255),
                notification.body.take(1000),
                notification.read,
                OffsetDateTime.ofInstant(
                    seedTime.minusSeconds(notification.hoursAgo * 3_600),
                    ZoneOffset.UTC,
                ),
            )
        }
        log.info("seeded {} new demo notifications for {}", insertedCount, "student@demo.campusute.vn")
    }
}
