package box.tapsi.libs.scheduler.scheduler.aop.interceptors

import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.DefaultScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.utilities.time.TimeOperator
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

@Component(RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME)
@Suppress("TooManyFunctions")
class RetryJobInterceptor(
  private val timeOperator: TimeOperator,
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

  private fun getNextFireTime(): Instant = timeOperator.addToCurrentTime(
    FIXED_OFFSET_RETRY_MILLIS,
    TimeUnit.MILLISECONDS,
  )

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
    .thenReturn(getNextFireTime())
    .doOnNext {
      logger.info(
        "Job ${invocation.method.declaringClass.simpleName} will be retried at $it later",
      )
    }.flatMap { fireTimestamp ->
      scheduler.schedule(jobStore, fireTimestamp)
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
    const val FIXED_OFFSET_RETRY_MILLIS = 60 * 1000L
    const val RETRY_JOB_INTERCEPTOR_NAME = "retryJobInterceptor"
    const val JOB_STORE_INDEX = 0
  }
}
