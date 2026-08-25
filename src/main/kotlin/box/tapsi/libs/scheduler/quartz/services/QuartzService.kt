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

  /**
   * Adds [trigger] to the job it already references (`trigger.jobKey`). The retry path uses this to
   * attach the next one-shot retry trigger to the stable, non-durable job that is firing, so the job
   * keeps its identity across attempts and Quartz removes it once its last trigger completes.
   */
  fun scheduleTrigger(trigger: Trigger): Mono<Void>

  fun deleteJob(jobKey: JobKey): Mono<Void>
  fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void>
  fun getTriggers(triggerGroup: String): Flux<Trigger>
}
