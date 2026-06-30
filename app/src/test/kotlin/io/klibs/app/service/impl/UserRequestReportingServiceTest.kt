package io.klibs.app.service.impl

import BaseUnitWithDbLayerTest
import io.klibs.app.service.UserIssueNotifier
import io.klibs.core.pckg.entity.UserRequestIssueEntity
import io.klibs.core.pckg.entity.UserRequestReportEntity
import io.klibs.core.pckg.enums.UserRequestIndexingStatus
import io.klibs.core.pckg.enums.UserRequestProcessingStatus
import io.klibs.core.pckg.repository.UserRequestIssueRepository
import io.klibs.core.pckg.repository.UserRequestReportRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRequestReportingServiceTest : BaseUnitWithDbLayerTest() {

    @Autowired
    private lateinit var uut: UserRequestReportingService

    @Autowired
    private lateinit var issueRepository: UserRequestIssueRepository

    @Autowired
    private lateinit var reportRepository: UserRequestReportRepository

    @MockitoBean
    private lateinit var userIssueNotifier: UserIssueNotifier

    private val issueNumber = 100

    @Test
    fun `posts success comment and marks issue PROCESSED for a SUCCESS report on an ACCEPTED issue`() {
        val issue = saveIssue(UserRequestProcessingStatus.ACCEPTED)
        val report = saveReport(issue, UserRequestIndexingStatus.SUCCESS)

        assertTrue(uut.processReportsQueue())

        verify(userIssueNotifier).notifyIndexingSuccess(issueNumber)
        assertEquals(UserRequestProcessingStatus.PROCESSED, statusOf(issue))
        assertFalse(reportRepository.existsById(requireNotNull(report.id)))
    }

    @Test
    fun `posts failure comment and marks issue FAILED for a FAILURE report on an ACCEPTED issue`() {
        val issue = saveIssue(UserRequestProcessingStatus.ACCEPTED)
        val report = saveReport(issue, UserRequestIndexingStatus.FAILURE, statusDetails = "test error")

        uut.processReportsQueue()

        verify(userIssueNotifier).notifyIndexingFailure(issueNumber, "test error")
        val reloaded = issueRepository.findById(requireNotNull(issue.id)).get()
        assertEquals(UserRequestProcessingStatus.FAILED, reloaded.processingStatus)
        assertEquals("test error", reloaded.statusDetails)
        assertFalse(reportRepository.existsById(requireNotNull(report.id)))
    }

    @Test
    fun `posts success comment and marks issue PROCESSED for a SUCCESS report on a FAILED issue`() {
        val issue = saveIssue(UserRequestProcessingStatus.FAILED)
        saveReport(issue, UserRequestIndexingStatus.SUCCESS)

        uut.processReportsQueue()

        verify(userIssueNotifier).notifyIndexingSuccess(issueNumber)
        assertEquals(UserRequestProcessingStatus.PROCESSED, statusOf(issue))
    }

    @Test
    fun `drops a FAILURE report without commenting when the issue already FAILED`() {
        val issue = saveIssue(UserRequestProcessingStatus.FAILED)
        val report = saveReport(issue, UserRequestIndexingStatus.FAILURE, statusDetails = "again")

        uut.processReportsQueue()

        verifyNoInteractions(userIssueNotifier)
        assertEquals(UserRequestProcessingStatus.FAILED, statusOf(issue))
        assertFalse(reportRepository.existsById(requireNotNull(report.id)))
    }

    @Test
    fun `drops a report without commenting when the issue is already PROCESSED`() {
        val issue = saveIssue(UserRequestProcessingStatus.PROCESSED)
        val report = saveReport(issue, UserRequestIndexingStatus.SUCCESS)

        uut.processReportsQueue()

        verifyNoInteractions(userIssueNotifier)
        assertEquals(UserRequestProcessingStatus.PROCESSED, statusOf(issue))
        assertFalse(reportRepository.existsById(requireNotNull(report.id)))
    }

    @Test
    fun `increments report attempts and leaves the issue untouched when posting the comment fails`() {
        val issue = saveIssue(UserRequestProcessingStatus.ACCEPTED)
        val report = saveReport(issue, UserRequestIndexingStatus.SUCCESS)
        doThrow(RuntimeException("GitHub down"))
            .whenever(userIssueNotifier).notifyIndexingSuccess(any())

        uut.processReportsQueue()

        assertEquals(UserRequestProcessingStatus.ACCEPTED, statusOf(issue))
        assertEquals(1, reportRepository.findById(requireNotNull(report.id)).get().failedAttempts)
    }

    @Test
    fun `omits a report whose issue is in NEW status`() {
        val issue = saveIssue(UserRequestProcessingStatus.NEW)
        val report = saveReport(issue, UserRequestIndexingStatus.SUCCESS)

        uut.processReportsQueue()

        verifyNoInteractions(userIssueNotifier)
        assertEquals(UserRequestProcessingStatus.NEW, statusOf(issue))
        assertEquals(1, reportRepository.findById(requireNotNull(report.id)).get().failedAttempts)
    }

    @Test
    fun `omits a report whose issue is in REJECTED status`() {
        val issue = saveIssue(UserRequestProcessingStatus.REJECTED)
        val report = saveReport(issue, UserRequestIndexingStatus.SUCCESS)

        uut.processReportsQueue()

        verifyNoInteractions(userIssueNotifier)
        assertEquals(UserRequestProcessingStatus.REJECTED, statusOf(issue))
        assertEquals(1, reportRepository.findById(requireNotNull(report.id)).get().failedAttempts)
    }

    @Test
    fun `returns false when the queue is empty`() {
        assertFalse(uut.processReportsQueue())
    }

    private fun saveIssue(status: UserRequestProcessingStatus) = issueRepository.save(
        UserRequestIssueEntity(
            githubIssueNumber = issueNumber,
            groupId = "com.example",
            artifactId = "lib",
            version = "1.0.0",
            processingStatus = status,
        )
    )

    private fun saveReport(
        issue: UserRequestIssueEntity,
        indexingStatus: UserRequestIndexingStatus,
        statusDetails: String? = null,
    ) = reportRepository.save(
        UserRequestReportEntity(
            userRequestIssue = issue,
            groupId = "com.example",
            artifactId = "lib",
            version = "1.0.0",
            indexingStatus = indexingStatus,
            statusDetails = statusDetails,
        )
    )

    private fun statusOf(issue: UserRequestIssueEntity) =
        issueRepository.findById(requireNotNull(issue.id)).get().processingStatus
}
