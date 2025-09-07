package box.tapsi.libs.scheduler.scheduler.services

import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SchedulerService {
  fun scheduleRegularJob(instruction: SchedulingInstruction.Regular): Mono<Void>
  fun deleteJob(jobId: String, jobGroup: JobGroup): Mono<Void>
  fun scheduleCronJob(instruction: SchedulingInstruction.Cron): Mono<Void>
  fun getTriggers(triggerGroup: TriggerGroup): Flux<Trigger>
  fun reschedule(triggerId: String, trigger: Trigger): Mono<Void>
}
