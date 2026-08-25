package box.tapsi.libs.scheduler.scheduler.aop.interceptors

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.jobs.DefaultSchedulerJob
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.DefaultScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.scheduler.scheduler.toJobDataMap
import box.tapsi.libs.utilities.time.TimeOperator
import box.tapsi.libs.utilities.time.toDate
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.quartz.JobKey
import org.quartz.ObjectAlreadyExistsException
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

@Suppress("TooManyFunctions")
class RetryJobInterceptor(
  private val timeOperator: TimeOperator,
  private val quartzRegistry: QuartzRegistry,
  private val quartzService: QuartzService,
) : MethodInterceptor {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun invoke(invocation: MethodInvocation): Any? {
    val methodName = invocation.method.name
    if (!isSchedulerExecuteMethod(methodName)) {
      return invocation.proceed()
    }
    val reactiveRetryable = AnnotatedElementUtils.findMergedAnnotation(invocation.method, ReactiveRetryable::class.java)
      ?: classLevelAnnotation(invocation.method, ReactiveRetryable::class.java)
      ?: findAnnotationOnTarget(invocation.`this`!!, invocation.method, ReactiveRetryable::class.java)
    return if (isJobClass<DefaultScheduler>(invocation.method.declaringClass) && reactiveRetryable != null) {
      handleJob<DefaultScheduler>(invocation, reactiveRetryable)
    } else {
      invocation.proceed()
    }
  }

  private fun <T : Annotation> classLevelAnnotation(
    method: Method,
    annotationClass: Class<T>,
  ): T? = AnnotatedElementUtils.findMergedAnnotation(method.declaringClass, annotationClass)

  private fun computeNextFireTime(reactiveRetryable: ReactiveRetryable, retryCount: Int): Instant {
    val delay = computeDelayMillis(reactiveRetryable, retryCount)
    return timeOperator.addToCurrentTime(delay, TimeUnit.MILLISECONDS)
  }

  internal fun computeDelayMillis(reactiveRetryable: ReactiveRetryable, retryCount: Int): Long {
    if (reactiveRetryable.exponentialBackoff) {
      val minDelay = reactiveRetryable.backOffMinDelay.takeIf { it > 0 } ?: FIXED_OFFSET_RETRY_MILLIS
      val maxDelay = reactiveRetryable.backOffMaxDelay.takeIf { it > 0 } ?: Long.MAX_VALUE
      val factor = reactiveRetryable.backOffFactor.takeIf { it > 0 } ?: DEFAULT_BACKOFF_FACTOR
      return (minDelay * factor.pow(retryCount.toDouble())).toLong().coerceAtMost(maxDelay)
    }
    return reactiveRetryable.backOffFixDelay.takeIf { it > 0 } ?: FIXED_OFFSET_RETRY_MILLIS
  }

  @Suppress("SpreadOperator", "TooGenericExceptionCaught")
  private fun <T : Annotation> findAnnotationOnTarget(target: Any, method: Method, annotation: Class<T>): T? = try {
    val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
    AnnotatedElementUtils.findMergedAnnotation(targetMethod, annotation) ?: classLevelAnnotation(
      targetMethod,
      annotation,
    )
  } catch (exception: Exception) {
    logger.error("Error finding annotation on target", exception)
    null
  }

  private fun canRetry(
    err: KClass<out Throwable>,
    include: Array<KClass<out Throwable>>,
    exclude: Array<KClass<out Throwable>>,
  ): Boolean = if (exclude.any { ex -> ex.isSuperclassOf(err) }) {
    false
  } else if (include.isEmpty()) {
    true
  } else {
    include.any { ex -> ex.isSuperclassOf(err) }
  }

  private fun isSchedulerExecuteMethod(methodName: String): Boolean = methodName == RegularScheduler::execute.name ||
    methodName == CronScheduler::execute.name

  private fun <TJob : Any> isJobClass(declaringClass: Class<*>): Boolean = declaringClass as? Class<out TJob> != null

  private fun <TJob : DefaultScheduler> handleJob(
    invocation: MethodInvocation,
    reactiveRetryable: ReactiveRetryable,
  ): Mono<Void> {
    val scheduler = invocation.`this` as? TJob ?: return invocation.proceed() as Mono<Void>
    val jobStore = invocation.arguments[JOB_STORE_INDEX] as JobStore
    putRetryCountIfNecessary(invocation, jobStore)
    return if (checkRetryAttemptsLimit(jobStore, reactiveRetryable)) {
      invocation.proceed() as Mono<Void>
    } else {
      (invocation.proceed() as Mono<Void>).onErrorResume {
        logger.error("Error while executing job ${scheduler::class.simpleName}", it)
        if (canRetry(it::class, reactiveRetryable.include, reactiveRetryable.exclude)) {
          logger.info("Job ${scheduler::class.simpleName} included in retry on ${it::class}")
          val retryCount = jobStore.getInt(RETRY_COUNT_JOB_STORE_KEY)!!
          jobStore.put(RETRY_COUNT_JOB_STORE_KEY, retryCount + 1)
          return@onErrorResume retryJob(invocation, retryCount, jobStore, reactiveRetryable, scheduler)
            .then(Mono.error(it))
        }
        logger.info("Job ${invocation.method.name} excluded from retry on ${it::class}")
        return@onErrorResume Mono.error<Void>(it)
      }
    }
  }

  private fun putRetryCountIfNecessary(
    invocation: MethodInvocation,
    jobStore: JobStore,
  ) {
    if (!jobStore.contains(RETRY_COUNT_JOB_STORE_KEY)) {
      jobStore.put(RETRY_COUNT_JOB_STORE_KEY, 0)
      updateJobStore(invocation, jobStore)
    }
  }

  private fun updateJobStore(invocation: MethodInvocation, jobStore: JobStore) {
    invocation.arguments[JOB_STORE_INDEX] = jobStore
  }

  private fun checkRetryAttemptsLimit(jobStore: JobStore, reactiveRetryable: ReactiveRetryable): Boolean {
    val retryCount = jobStore.getInt(RETRY_COUNT_JOB_STORE_KEY)!!
    return retryCount > reactiveRetryable.maxAttempts
  }

  private fun <TJob : DefaultScheduler> retryJob(
    invocation: MethodInvocation,
    retryCount: Int,
    jobStore: JobStore,
    reactiveRetryable: ReactiveRetryable,
    scheduler: TJob,
  ): Mono<Void> = checkExhaustedRetry(retryCount, reactiveRetryable, invocation.method.declaringClass.simpleName)
    .doOnError(SchedulerException.ExhaustedJobRetryException::class.java) {
      incrementRetryCounter(scheduler, QuartzRegistry.RetryResult.Exhausted)
    }
    .thenReturn(computeNextFireTime(reactiveRetryable, retryCount))
    .doOnNext {
      incrementRetryCounter(scheduler, QuartzRegistry.RetryResult.Retried)
      logger.info(
        "Job ${invocation.method.declaringClass.simpleName} will be retried at $it later",
      )
    }.flatMap { fireTimestamp ->
      scheduleNativeRetry(scheduler, jobStore, fireTimestamp)
    }

  /**
   * Schedules the next attempt with a Quartz-native trigger, not a new job identity.
   *
   * A regular scheduler — and a cron scheduler that already fires from its retry job — adds a
   * one-shot retry trigger to the firing job's own, stable key. A cron scheduler firing on its
   * cadence spawns a separate `<jobId>_retry` regular job, so the recurring cron trigger stays
   * untouched.
   *
   * The firing job key is rebuilt from the scheduler, not read from the [org.quartz.JobExecutionContext].
   * The job id is stable ([box.tapsi.libs.scheduler.scheduler.getCompositeJobId] no longer changes on
   * retry), so `createJobId` returns the base name, and the [CRON_RETRY_JOB_STORE_KEY] flag adds the
   * retry suffix. This keeps the retry independent of the reactor-context seam.
   */
  private fun scheduleNativeRetry(
    scheduler: DefaultScheduler,
    jobStore: JobStore,
    fireTimestamp: Instant,
  ): Mono<Void> = Mono.defer {
    val baseJobId = scheduler.createJobId(jobStore)
    val jobGroup = scheduler.getJobGroup().value
    return@defer if (scheduler is CronScheduler && !jobStore.contains(CRON_RETRY_JOB_STORE_KEY)) {
      spawnCronRetryJob(scheduler, baseJobId, jobGroup, jobStore, fireTimestamp)
    } else {
      val firingJobName = if (jobStore.contains(CRON_RETRY_JOB_STORE_KEY)) {
        "$baseJobId$CRON_RETRY_JOB_SUFFIX"
      } else {
        baseJobId
      }
      addRetryTrigger(scheduler, JobKey(firingJobName, jobGroup), jobStore, fireTimestamp)
    }
  }

  /**
   * Adds a one-shot retry trigger to the firing, non-durable job before its current fire completes.
   * The job then keeps its identity across attempts, and Quartz removes it once the retries stop and
   * its last trigger completes. The failed run's store rides the trigger data map.
   */
  private fun addRetryTrigger(
    scheduler: DefaultScheduler,
    firingJobKey: JobKey,
    jobStore: JobStore,
    fireTimestamp: Instant,
  ): Mono<Void> = Mono.defer {
    val attempt = jobStore.getInt(RETRY_COUNT_JOB_STORE_KEY) ?: 0
    val retryTrigger = TriggerBuilder.newTrigger()
      .withIdentity("${firingJobKey.name}_retry_$attempt", scheduler.getTriggerGroup().value)
      .forJob(firingJobKey)
      .startAt(fireTimestamp.toDate())
      .usingJobData(jobStore.toJobDataMap())
      .build()
    quartzService.scheduleTrigger(retryTrigger)
  }

  /**
   * Spawns a separate `<jobId>_retry` regular job for a failed cron occurrence, so the recurring
   * cron job keeps its cadence and its store. The [CRON_RETRY_JOB_STORE_KEY] flag marks the spawned
   * job, so its own later failures retry in place instead of spawning again.
   */
  private fun spawnCronRetryJob(
    scheduler: DefaultScheduler,
    baseJobId: String,
    jobGroup: String,
    jobStore: JobStore,
    fireTimestamp: Instant,
  ): Mono<Void> = Mono.defer {
    val retryJobName = "$baseJobId$CRON_RETRY_JOB_SUFFIX"
    val jobDataMap = jobStore.toJobDataMap().apply { put(CRON_RETRY_JOB_STORE_KEY, true) }
    val retryJobDetail = quartzService.createJob(
      jobClass = DefaultSchedulerJob::class.java,
      isDurable = false,
      jobName = retryJobName,
      jobGroup = jobGroup,
      jobDataMap = jobDataMap,
    )
    val retryTrigger = TriggerBuilder.newTrigger()
      .withIdentity("${retryJobName}_trigger", scheduler.getTriggerGroup().value)
      .startAt(fireTimestamp.toDate())
      .build()
    // Policy (a): one retry in flight per cron scheduler. If the retry job is already pending from
    // an earlier failed occurrence, the duplicate is dropped. Exhaustion clears the pending retry
    // job (it adds no trigger and completes), so a later cadence failure re-creates it — the two
    // orderings both hold: a swallowed spawn while it exhausts, or a re-create after it cleared.
    quartzService.scheduleJob(retryJobDetail, retryTrigger)
      .onErrorComplete(ObjectAlreadyExistsException::class.java)
  }

  private fun incrementRetryCounter(scheduler: DefaultScheduler, result: QuartzRegistry.RetryResult) {
    quartzRegistry.incrementRetry(
      job = scheduler::class.simpleName ?: UNKNOWN_JOB,
      jobGroup = scheduler.getJobGroup().value,
      result = result,
    )
  }

  private fun checkExhaustedRetry(retryCount: Int, reactiveRetryable: ReactiveRetryable, jobName: String): Mono<Void> {
    if (retryCount + 1 > reactiveRetryable.maxAttempts) {
      logger.error("Retry exhausted for job $jobName with $retryCount attempts")
      return Mono.error(
        SchedulerException.ExhaustedJobRetryException(
          "Retry exhausted for " +
            "job $jobName with $retryCount attempts",
        ),
      )
    }
    return Mono.empty()
  }

  companion object {
    const val RETRY_COUNT_JOB_STORE_KEY = "retryCount"

    /** Marks a spawned cron retry job, so its own later failures retry in place, not spawn again. */
    const val CRON_RETRY_JOB_STORE_KEY = "cronRetryJob"

    /** Suffix of the stable id of the separate regular job that retries a failed cron occurrence. */
    const val CRON_RETRY_JOB_SUFFIX = "_retry"
    const val FIXED_OFFSET_RETRY_MILLIS = 60 * 1000L
    const val DEFAULT_BACKOFF_FACTOR = 2.0
    const val RETRY_JOB_INTERCEPTOR_NAME = "retryJobInterceptor"
    const val JOB_STORE_INDEX = 0
    private const val UNKNOWN_JOB = "unknown"
  }
}
