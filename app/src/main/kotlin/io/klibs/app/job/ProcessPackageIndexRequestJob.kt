package io.klibs.app.job

import io.klibs.app.indexing.PackageIndexingService
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(
    value = ["klibs.scheduling.process-indexing-queue.enabled"],
    havingValue = "true",
)
class ProcessPackageIndexRequestJob(val packageIndexingService: PackageIndexingService) {

    @Scheduled(initialDelay = 0, fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "processPackageIndexRequestsLock", lockAtMostFor = "10m")
    fun processPackageIndexRequests() {
        LockAssert.assertLocked();
        while (packageIndexingService.processPackageQueue()) {

        }
    }
}