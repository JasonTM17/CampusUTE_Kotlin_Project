package com.campusute.backend.note

import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notes")
class Note(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false) var title: String,
    @Column(nullable = false) var content: String = "",
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: Instant = Instant.EPOCH,
)

interface NoteRepository : JpaRepository<Note, UUID> {
    fun findByUserIdOrderByUpdatedAtDesc(userId: UUID): List<Note>
    fun findByIdAndUserId(id: UUID, userId: UUID): Note?
}

data class NoteDto(val id: UUID, val title: String, val content: String, val updatedAt: String)
data class NoteRequest(val title: String, val content: String)

/** Ownership by query scoping: a user can only ever touch their own notes. */
@Tag(name = "notes")
@RestController
@RequestMapping("/api/v1/notes")
class NoteController(private val notes: NoteRepository) {

    @GetMapping
    fun list(): ApiEnvelope<List<NoteDto>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        return ApiEnvelope.ok(notes.findByUserIdOrderByUpdatedAtDesc(userId).map { it.toDto() })
    }

    @PostMapping
    fun create(@RequestBody body: NoteRequest): ApiEnvelope<NoteDto> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        if (body.title.isBlank()) throw ApiException(ErrorCode.VALIDATION_FAILED, "Tiêu đề không được trống.")
        return ApiEnvelope.ok(notes.save(Note(userId = userId, title = body.title.take(255), content = body.content.take(50_000))).toDto())
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: NoteRequest): ApiEnvelope<NoteDto> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val note = notes.findByIdAndUserId(id, userId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy ghi chú.")
        note.title = body.title.take(255)
        note.content = body.content.take(50_000)
        note.updatedAt = Instant.now() // DB column is default-managed; bump explicitly
        return ApiEnvelope.ok(notes.save(note).toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ApiEnvelope<Map<String, String>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val note = notes.findByIdAndUserId(id, userId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy ghi chú.")
        notes.delete(note)
        return ApiEnvelope.ok(mapOf("status" to "DELETED"))
    }

    private fun Note.toDto() = NoteDto(id, title, content, updatedAt.toString())
}
