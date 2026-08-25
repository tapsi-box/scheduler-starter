package box.tapsi.libs.scheduler.docs

import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Every Kotlin snippet in README.md, copied verbatim so the compiler proves the documentation is
 * correct. Change a snippet here and in the README together.
 */

// region Quick Start - Create Scheduled Jobs

@Component
class EmailNotificationScheduler(
  schedulerService: SchedulerService,
) : RegularScheduler(schedulerService) {

  override fun execute(jobStore: JobStore?): Mono<Void> = Mono.fromRunnable<Void> {
    // Send email notification logic
    println("Sending email notification...")
  }.then()

  override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()

  override fun createJobId(jobStore: JobStore): String = "email-notification"
  override fun getJobGroup(): JobGroup = JobGroup.fromString("notifications")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("email-triggers")
}

@Component
class DataCleanupScheduler(
  schedulerService: SchedulerService,
) : CronScheduler(schedulerService) {

  override fun getCronExpression(): String = "0 0 2 * * ?" // Daily at 2 AM

  override fun execute(jobStore: JobStore?): Mono<Void> = Mono.fromRunnable<Void> {
    // Data cleanup logic
    println("Cleaning up old data...")
  }.then()

  override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()

  override fun createJobId(jobStore: JobStore): String = "data-cleanup"
  override fun getJobGroup(): JobGroup = JobGroup.fromString("maintenance")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("cleanup-triggers")
}

// endregion

// region Usage Examples - Regular Job Scheduling

@Service
class TaskService(
  private val schedulerService: SchedulerService,
) {

  fun scheduleTask(taskId: String, delayMinutes: Long) {
    val fireTime = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES)

    val instruction = SchedulingInstruction.Regular(
      fireTimestamp = fireTime,
      scheduler = EmailNotificationScheduler::class,
      jobStore = JobStore(mutableMapOf("taskId" to taskId)),
      jobId = "task-$taskId",
      jobGroup = JobGroup.fromString("tasks"),
      triggerGroup = TriggerGroup.fromString("task-triggers"),
    )

    schedulerService.scheduleRegularJob(instruction)
      .subscribe(
        { println("Task scheduled successfully") },
        { error -> println("Failed to schedule task: $error") },
      )
  }
}

// endregion

// region Usage Examples - Cron Job Scheduling

@Component
class ReportGenerationScheduler(
  schedulerService: SchedulerService,
) : CronScheduler(schedulerService) {

  override fun getCronExpression(): String = "0 0 9 * * MON-FRI" // Weekdays at 9 AM

  override fun execute(jobStore: JobStore?): Mono<Void> = Mono.fromRunnable<Void> {
    // Generate daily reports
    generateReports()
  }.then()

  override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()

  override fun createJobId(jobStore: JobStore): String = "report-generation"
  override fun getJobGroup(): JobGroup = JobGroup.fromString("reports")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("report-triggers")

  private fun generateReports() {
    // Report generation logic
  }
}

// endregion

// region Usage Examples - Job Management

@Service
class JobManagementService(
  private val schedulerService: SchedulerService,
) {

  fun cancelJob(jobId: String) {
    schedulerService.deleteJob(jobId, JobGroup.fromString("tasks"))
      .subscribe(
        { println("Job cancelled successfully") },
        { error -> println("Failed to cancel job: $error") },
      )
  }

  fun rescheduleJob(triggerId: String, newCronExpression: String) {
    val newTrigger = Trigger.CronTrigger(
      jobGroup = JobGroup.fromString("tasks"),
      jobId = "task-$triggerId",
      triggerGroup = TriggerGroup.fromString("task-triggers"),
      triggerId = triggerId,
      startTimestamp = Instant.now(),
      cronExpression = newCronExpression,
    )

    schedulerService.reschedule(triggerId, newTrigger)
      .subscribe(
        { println("Job rescheduled successfully") },
        { error -> println("Failed to reschedule job: $error") },
      )
  }

  fun getActiveTriggers(): Flux<Trigger> = schedulerService.getTriggers(TriggerGroup.fromString("task-triggers"))
}

// endregion

// region Testing section

@Suppress("UnusedPrivateMember")
private fun readmeTestingSnippets(schedulerService: SchedulerService) {
  val fireTime = Instant.now().plus(1, ChronoUnit.MINUTES)
  val regular = SchedulingInstruction.Regular(
    fireTimestamp = fireTime,
    scheduler = EmailNotificationScheduler::class,
    jobStore = JobStore(),
    jobId = "test-job",
    jobGroup = JobGroup.fromString("test"),
    triggerGroup = TriggerGroup.fromString("test-triggers"),
  )
  val cron = SchedulingInstruction.Cron(
    fireTimestamp = null,
    cronExpression = "0 0 12 * * ?",
    scheduler = DataCleanupScheduler::class,
    jobStore = JobStore(),
    jobId = "test-cron-job",
    jobGroup = JobGroup.fromString("test"),
    triggerGroup = TriggerGroup.fromString("test-triggers"),
  )
  schedulerService.scheduleRegularJob(regular)
  schedulerService.scheduleCronJob(cron)
}

// endregion
