package box.tapsi.libs.scheduler.quartz.services

import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface QuartzService {
  fun <TJob : Job> createJob(
    jobClass: Class<TJob>,
    isDurable: Boolean,
    jobName: String,
    jobGroup: String,
    jobDataMap: JobDataMap,
  ): JobDetail

  fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void>
  fun deleteJob(jobKey: JobKey): Mono<Void>
  fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void>
  fun getTriggers(triggerGroup: String): Flux<Trigger>
}
