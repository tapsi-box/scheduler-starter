package box.tapsi.libs.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("box.libs.scheduler")
data class SchedulerProperties(
  val quartz: Quartz = Quartz(),
  val cronJob: CronJob = CronJob(),
) {
  data class Quartz(val enabled: Boolean = true)
  data class CronJob(
    val schedulingEnabled: Boolean = false,
    val schedulingExcludes: List<String> = emptyList(),
  )
}
