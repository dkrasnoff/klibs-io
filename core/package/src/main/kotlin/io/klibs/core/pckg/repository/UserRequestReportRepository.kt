package io.klibs.core.pckg.repository

import io.klibs.core.pckg.entity.UserRequestReportEntity
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface UserRequestReportRepository : CrudRepository<UserRequestReportEntity, Long> {

    @Query(
        value = """
            SELECT report.*
            FROM user_request_report report
            WHERE report.failed_attempts < 2
            ORDER BY report.id
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findFirstForReporting(): UserRequestReportEntity?

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(
        value = """
            UPDATE user_request_report
            SET failed_ts = current_timestamp,
                failed_attempts = failed_attempts + 1,
                last_error_message = :errorMessage
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun markAsFailed(@Param("id") id: Long, @Param("errorMessage") errorMessage: String?)
}