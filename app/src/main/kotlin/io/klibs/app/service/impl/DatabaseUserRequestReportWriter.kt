package io.klibs.app.service.impl

import io.klibs.app.service.UserRequestReportWriter
import io.klibs.core.pckg.entity.UserRequestReportEntity
import io.klibs.core.pckg.enums.UserRequestIndexingStatus
import io.klibs.core.pckg.repository.IndexingRequestRepository
import io.klibs.core.pckg.repository.UserRequestReportRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class DatabaseUserRequestReportWriter(
    private val indexingRequestRepository: IndexingRequestRepository,
    private val userRequestReportRepository: UserRequestReportRepository,
) : UserRequestReportWriter {

    @Transactional
    override fun saveSuccessReport(indexRequestId: Long) {
        val indexRequest = indexingRequestRepository.findById(indexRequestId).orElse(null) ?: return
        val issue = indexRequest.userRequestIssue ?: return
        val version = indexRequest.version ?: return
        userRequestReportRepository.save(
            UserRequestReportEntity(
                userRequestIssue = issue,
                groupId = indexRequest.groupId,
                artifactId = indexRequest.artifactId,
                version = version,
                indexingStatus = UserRequestIndexingStatus.SUCCESS,
            )
        )
    }

    /**
     * Relies on the caller running markAsFailed first: that commit increments
     * the counter, so the reload below already counts this attempt.
     */
    @Transactional
    override fun saveFailureReportIfTerminal(indexRequestId: Long, errorMessage: String?) {
        val indexRequest = indexingRequestRepository.findById(indexRequestId).orElse(null) ?: return
        val issue = indexRequest.userRequestIssue ?: return
        if (indexRequest.failedAttempts < MAX_INDEXING_ATTEMPTS) return
        val version = indexRequest.version ?: return
        userRequestReportRepository.save(
            UserRequestReportEntity(
                userRequestIssue = issue,
                groupId = indexRequest.groupId,
                artifactId = indexRequest.artifactId,
                version = version,
                indexingStatus = UserRequestIndexingStatus.FAILURE,
                statusDetails = errorMessage,
            )
        )
    }

    internal companion object {
        internal const val MAX_INDEXING_ATTEMPTS = 2
    }
}
