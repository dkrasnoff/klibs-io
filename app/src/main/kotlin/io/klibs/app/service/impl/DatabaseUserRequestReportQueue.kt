package io.klibs.app.service.impl

import io.klibs.app.dto.UserRequestReport
import io.klibs.app.service.UserRequestReportQueue
import io.klibs.core.pckg.entity.UserRequestReportEntity
import io.klibs.core.pckg.repository.UserRequestReportRepository
import org.springframework.stereotype.Service

@Service
class DatabaseUserRequestReportQueue(
    private val userRequestReportRepository: UserRequestReportRepository,
) : UserRequestReportQueue {

    override fun poll(): UserRequestReport? =
        userRequestReportRepository.findFirstForReporting()?.toReport()

    override fun markAsSuccess(report: UserRequestReport) {
        userRequestReportRepository.deleteById(report.reportId)
    }

    override fun markAsFailed(report: UserRequestReport, errorMessage: String?) {
        userRequestReportRepository.markAsFailed(report.reportId, errorMessage)
    }

    private fun UserRequestReportEntity.toReport() = UserRequestReport(
        reportId = requireNotNull(id) {
            "UserRequestReportEntity.id is null — entity was not persisted"
        },
        userRequestIssueId = requireNotNull(userRequestIssue.id) {
            "UserRequestReportEntity.userRequestIssue.id is null — entity was not persisted"
        },
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        indexingStatus = indexingStatus,
        statusDetails = statusDetails,
    )
}
