package io.klibs.app.service

import io.klibs.app.dto.UserRequestReport

/**
 * Queue of indexing-result reports waiting to be posted back to their GitHub issues.
 *
 * Kept behind an interface so the database-backed queue can later be replaced by a
 * message broker without changing the reporting logic.
 */
interface UserRequestReportQueue {
    /**
     * Returns the next report to publish, or null when there is nothing to do.
     */
    fun poll(): UserRequestReport?

    /**
     * Acknowledges a report that was published successfully and removes it from the queue.
     */
    fun markAsSuccess(report: UserRequestReport)

    /**
     * Records a failed publishing attempt so the report can be retried later.
     */
    fun markAsFailed(report: UserRequestReport, errorMessage: String?)
}
