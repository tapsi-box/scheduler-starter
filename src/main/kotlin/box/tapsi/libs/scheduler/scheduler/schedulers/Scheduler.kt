package box.tapsi.libs.scheduler.scheduler.schedulers

import box.tapsi.libs.scheduler.scheduler.store.JobStore
import reactor.core.publisher.Mono
import java.time.Instant

interface Scheduler {
  fun schedule(jobStore: JobStore?, fireTimestamp: Instant?): Mono<Void>
  fun execute(jobStore: JobStore?): Mono<Void>
  fun cancel(jobStore: JobStore?): Mono<Void>
}
