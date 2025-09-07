package box.tapsi.libs.scheduler.autoconfigure

import box.tapsi.libs.scheduler.SchedulerProperties
import box.tapsi.libs.scheduler.scheduler.autoconfigure.CronJobSchedulerAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

@AutoConfiguration(after = [QuartzAutoConfiguration::class])
@EnableConfigurationProperties(SchedulerProperties::class)
@ComponentScan("box.tapsi.libs.scheduler")
class SchedulerAutoConfiguration {
  @ConditionalOnProperty(
    value = ["box.libs.scheduler.cron-job.scheduling-enabled"],
    havingValue = "true",
    matchIfMissing = false,
  )
  @Bean
  fun scheduleCronJobs(
    applicationContext: ApplicationContext,
    schedulerProperties: SchedulerProperties,
  ): CronJobSchedulerAutoConfiguration = CronJobSchedulerAutoConfiguration(applicationContext, schedulerProperties)
}
