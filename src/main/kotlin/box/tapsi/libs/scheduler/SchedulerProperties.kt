package box.tapsi.libs.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("box.libs.scheduler")
data class SchedulerProperties(
  val quartz: Quartz = Quartz(),
  val cronJob: CronJob = CronJob(),
) {
  data class Quartz(val enabled: Boolean = true)

  data class CronJob(
    val schedulingEnabled: Boolean = false,
    val schedulingExcludes: List<String> = emptyList(),
    /**
     * How long the startup waits for the cron jobs to be scheduled. The application fails to
     * start if the scheduling does not finish in this time.
     */
    val schedulingTimeout: Duration = DEFAULT_SCHEDULING_TIMEOUT,
  )

  companion object {
    private val DEFAULT_SCHEDULING_TIMEOUT: Duration = Duration.ofSeconds(60)
  }
}
