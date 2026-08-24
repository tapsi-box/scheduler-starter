package box.tapsi.libs.scheduler.docs

import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import box.tapsi.libs.scheduler.scheduler.aop.interceptors.RetryJobInterceptor
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.EnableReactiveRetry
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Configuration
@EnableReactiveRetry
class SchedulerRetryConfiguration

@Component
@ReactiveRetryable(
  interceptor = RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME,
  maxAttempts = 5,
  exponentialBackoff = true,
  backOffMinDelay = 60_000,
  backOffFactor = 2.0,
  backOffMaxDelay = 3_600_000,
  include = [IllegalStateException::class],
  exclude = [IllegalArgumentException::class],
)
class RetryableTaskScheduler(
  schedulerService: SchedulerService,
) : RegularScheduler(schedulerService) {
  override fun execute(jobStore: JobStore?): Mono<Void> = Mono.fromRunnable<Void> {
    performUnreliableOperation()
  }.then()

  override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()

  override fun createJobId(jobStore: JobStore): String = "retryable-task"
  override fun getJobGroup(): JobGroup = JobGroup.fromString("retryable-tasks")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("retry-triggers")

  private fun performUnreliableOperation() = Unit
}
