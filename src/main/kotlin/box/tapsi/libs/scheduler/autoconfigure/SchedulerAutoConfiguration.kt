package box.tapsi.libs.scheduler.autoconfigure

import box.tapsi.libs.metrics.core.services.MeterRegistryService
import box.tapsi.libs.scheduler.SchedulerProperties
import box.tapsi.libs.scheduler.quartz.annotations.OnQuartzEnabled
import box.tapsi.libs.scheduler.quartz.metric.config.SchedulerMetricsConfiguration
import box.tapsi.libs.scheduler.quartz.metric.listeners.QuartzMisfireTriggerListener
import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.quartz.services.NoOpQuartzService
import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.quartz.services.QuartzServiceImpl
import box.tapsi.libs.scheduler.scheduler.CronJobAutoScheduler
import box.tapsi.libs.scheduler.scheduler.aop.interceptors.RetryJobInterceptor
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import box.tapsi.libs.scheduler.scheduler.factories.QuartzTriggerFactory
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.services.SchedulerServiceImpl
import box.tapsi.libs.utilities.time.TimeOperator
import io.micrometer.observation.ObservationRegistry
import org.quartz.Scheduler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/**
 * Registers every bean of this library.
 *
 * Every bean is declared with a `@Bean` method on purpose. An auto-configuration must not use
 * `@ComponentScan`: a scanned component takes part in neither the auto-configuration order nor
 * the back-off that `@ConditionalOnMissingBean` depends on. Because the beans are declared here,
 * a consumer can replace any of them by simply defining its own bean of the same type.
 */
@AutoConfiguration(after = [QuartzAutoConfiguration::class])
@ConditionalOnClass(Scheduler::class)
@EnableConfigurationProperties(SchedulerProperties::class)
@Import(SchedulerMetricsConfiguration::class)
class SchedulerAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun quartzRegistry(
    meterRegistryService: MeterRegistryService,
    observationRegistry: ObservationRegistry,
  ): QuartzRegistry = QuartzRegistry(meterRegistryService, observationRegistry)

  @Bean
  @OnQuartzEnabled
  @ConditionalOnMissingBean
  fun quartzMisfireTriggerListener(
    quartzRegistry: QuartzRegistry,
    schedulerFactoryBean: SchedulerFactoryBean,
  ): QuartzMisfireTriggerListener = QuartzMisfireTriggerListener(quartzRegistry, schedulerFactoryBean)

  /**
   * The real Quartz-backed service. It is declared before [noOpQuartzService], so the no-op
   * fallback below sees this definition and backs off.
   */
  @Bean
  @OnQuartzEnabled
  @ConditionalOnMissingBean(QuartzService::class)
  fun quartzService(
    applicationContext: ApplicationContext,
    schedulerFactoryBean: SchedulerFactoryBean,
    quartzRegistry: QuartzRegistry,
    schedulerProperties: SchedulerProperties,
  ): QuartzService = QuartzServiceImpl(
    applicationContext = applicationContext,
    schedulerFactoryBean = schedulerFactoryBean,
    quartzRegistry = quartzRegistry,
    historyLoggingEnabled = schedulerProperties.quartz.historyLoggingEnabled,
  )

  /** The fallback used when Quartz is disabled. It builds job details but schedules nothing. */
  @Bean
  @ConditionalOnMissingBean(QuartzService::class)
  fun noOpQuartzService(applicationContext: ApplicationContext): QuartzService = NoOpQuartzService(applicationContext)

  @Bean
  @ConditionalOnMissingBean
  fun quartzTriggerFactory(timeOperator: TimeOperator): QuartzTriggerFactory = QuartzTriggerFactory(timeOperator)

  @Bean
  @ConditionalOnMissingBean
  fun quartzJobDetailFactory(
    quartzService: QuartzService,
    applicationContext: ApplicationContext,
  ): QuartzJobDetailFactory = QuartzJobDetailFactory(quartzService, applicationContext)

  @Bean
  @ConditionalOnMissingBean
  fun schedulerService(
    quartzTriggerFactory: QuartzTriggerFactory,
    quartzJobDetailFactory: QuartzJobDetailFactory,
    quartzService: QuartzService,
  ): SchedulerService = SchedulerServiceImpl(quartzTriggerFactory, quartzJobDetailFactory, quartzService)

  /**
   * The bean name is part of the public contract. A consumer names it in
   * `@ReactiveRetryable(interceptor = RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME)`, and
   * `projectreactor-retry-aop` resolves the interceptor from the context by that name.
   */
  @Bean(RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME)
  @ConditionalOnMissingBean
  fun retryJobInterceptor(
    timeOperator: TimeOperator,
    quartzRegistry: QuartzRegistry,
  ): RetryJobInterceptor = RetryJobInterceptor(timeOperator, quartzRegistry)

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
    value = ["box.libs.scheduler.cron-job.scheduling-enabled"],
    havingValue = "true",
    matchIfMissing = false,
  )
  fun cronJobAutoScheduler(
    applicationContext: ApplicationContext,
    schedulerProperties: SchedulerProperties,
  ): CronJobAutoScheduler = CronJobAutoScheduler(applicationContext, schedulerProperties)
}
