package box.tapsi.libs.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("box.libs.scheduler")
data class SchedulerProperties(
  val quartz: Quartz = Quartz(),
  val cronJob: CronJob = CronJob(),
  val job: Job = Job(),
) {
  data class Quartz(
    val enabled: Boolean = true,
    /**
     * Whether to install the Quartz history logging plugins. They log a line for every job fire
     * and every trigger fire, so they are off by default.
     */
    val historyLoggingEnabled: Boolean = false,
  )

  data class CronJob(
    val schedulingEnabled: Boolean = false,
    val schedulingExcludes: List<String> = emptyList(),
    /**
     * How long the startup waits for the cron jobs to be scheduled. The application fails to
     * start if the scheduling does not finish in this time.
     */
    val schedulingTimeout: Duration = DEFAULT_SCHEDULING_TIMEOUT,
  )

  data class Job(
    /**
     * How long a job execution may block a Quartz worker thread. No value means no limit, which
     * keeps the behaviour of a slow but healthy job unchanged.
     */
    val executionTimeout: Duration? = null,
  )

  companion object {
    private val DEFAULT_SCHEDULING_TIMEOUT: Duration = Duration.ofSeconds(60)
  }
}
