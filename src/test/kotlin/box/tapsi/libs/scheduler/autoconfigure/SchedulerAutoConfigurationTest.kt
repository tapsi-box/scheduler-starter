package box.tapsi.libs.scheduler.autoconfigure

import box.tapsi.libs.metrics.core.services.MeterRegistryService
import box.tapsi.libs.metrics.core.services.MeterRegistryServiceImpl
import box.tapsi.libs.scheduler.quartz.metric.listeners.QuartzMisfireTriggerListener
import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.quartz.services.NoOpQuartzService
import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.quartz.services.QuartzServiceImpl
import box.tapsi.libs.scheduler.scheduler.CronJobAutoScheduler
import box.tapsi.libs.scheduler.scheduler.aop.interceptors.RetryJobInterceptor
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import box.tapsi.libs.scheduler.scheduler.factories.QuartzTriggerFactory
import box.tapsi.libs.scheduler.scheduler.jobs.DefaultSchedulerJob
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.utilities.time.TimeOperator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/**
 * Verifies the bean wiring of [SchedulerAutoConfiguration].
 *
 * These tests start a real context. They are the only check that the move from `@ComponentScan`
 * to `@Bean` methods keeps every bean, keeps the Quartz enabled/disabled split, and lets a
 * consumer override a bean.
 */
class SchedulerAutoConfigurationTest {
  private val contextRunner = ApplicationContextRunner()
    .withConfiguration(
      AutoConfigurations.of(QuartzAutoConfiguration::class.java, SchedulerAutoConfiguration::class.java),
    )
    .withUserConfiguration(SupportingBeansConfiguration::class.java)

  @Test
  fun `should register every scheduler bean when quartz is enabled`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=true")

    // when + verify
    runner.run { context ->
      assertThat(context).hasSingleBean(QuartzRegistry::class.java)
      assertThat(context).hasSingleBean(QuartzTriggerFactory::class.java)
      assertThat(context).hasSingleBean(QuartzJobDetailFactory::class.java)
      assertThat(context).hasSingleBean(SchedulerService::class.java)
      assertThat(context).hasSingleBean(QuartzMisfireTriggerListener::class.java)
      assertThat(context).hasSingleBean(RetryJobInterceptor::class.java)
      assertThat(context).hasSingleBean(QuartzService::class.java)
      assertThat(context.getBean(QuartzService::class.java)).isInstanceOf(QuartzServiceImpl::class.java)
    }
  }

  @Test
  fun `should expose the retry interceptor under the name the retry annotation resolves`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=true")

    // when + verify
    runner.run { context ->
      assertThat(context).hasBean(RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME)
    }
  }

  /**
   * `DefaultSchedulerJob` is no longer a bean. Quartz builds one for every job fire through
   * `SpringBeanJobFactory`, which calls `createBean` on the class. This asserts that the same
   * call path still resolves all three constructor arguments from the context.
   */
  @Test
  fun `should construct the scheduler job the way quartz does`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=true")

    // when + verify
    runner.run { context ->
      val job = context.sourceApplicationContext.autowireCapableBeanFactory
        .createBean(DefaultSchedulerJob::class.java)
      assertThat(job).isNotNull()
    }
  }

  /**
   * `QuartzServiceImpl.afterPropertiesSet` discovers trigger listeners with
   * `getBeansWithAnnotation` and adds them to Quartz. The listener is now declared by a `@Bean`
   * method instead of being component-scanned, so this asserts the outcome: the listener really
   * reaches the Quartz listener manager. It also proves the init hook runs for the `@Bean` form.
   */
  @Test
  fun `should register the misfire listener with quartz`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=true")

    // when + verify
    runner.run { context ->
      val triggerListeners = context.getBean(SchedulerFactoryBean::class.java)
        .scheduler.listenerManager.triggerListeners
      assertThat(triggerListeners).anyMatch { it is QuartzMisfireTriggerListener }
    }
  }

  @Test
  fun `should fall back to the no-op quartz service when quartz is disabled`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=false")

    // when + verify
    runner.run { context ->
      assertThat(context).hasSingleBean(QuartzService::class.java)
      assertThat(context.getBean(QuartzService::class.java)).isInstanceOf(NoOpQuartzService::class.java)
      assertThat(context).doesNotHaveBean(QuartzMisfireTriggerListener::class.java)
    }
  }

  @Test
  fun `should fall back to the no-op quartz service when the enabled property is absent`() {
    // given
    val runner = contextRunner

    // when + verify
    runner.run { context ->
      assertThat(context.getBean(QuartzService::class.java)).isInstanceOf(NoOpQuartzService::class.java)
    }
  }

  @Test
  fun `should let a consumer replace a library bean`() {
    // given
    val runner = contextRunner
      .withPropertyValues("box.libs.scheduler.quartz.enabled=true")
      .withUserConfiguration(OverridingConfiguration::class.java)

    // when + verify
    runner.run { context ->
      assertThat(context).hasSingleBean(QuartzService::class.java)
      assertThat(context.getBean(QuartzService::class.java)).isSameAs(OverridingConfiguration.OVERRIDE)
    }
  }

  @Test
  fun `should not register the cron auto scheduler unless scheduling is enabled`() {
    // given
    val runner = contextRunner.withPropertyValues("box.libs.scheduler.quartz.enabled=true")

    // when + verify
    runner.run { context ->
      assertThat(context).doesNotHaveBean(CronJobAutoScheduler::class.java)
    }
  }

  @Test
  fun `should register the cron auto scheduler when scheduling is enabled`() {
    // given
    val runner = contextRunner.withPropertyValues(
      "box.libs.scheduler.quartz.enabled=true",
      "box.libs.scheduler.cron-job.scheduling-enabled=true",
    )

    // when + verify
    runner.run { context ->
      assertThat(context).hasSingleBean(CronJobAutoScheduler::class.java)
    }
  }

  @Configuration(proxyBeanMethods = false)
  class SupportingBeansConfiguration {
    @Bean
    fun meterRegistryService(): MeterRegistryService = MeterRegistryServiceImpl(SimpleMeterRegistry())

    @Bean
    fun observationRegistry(): ObservationRegistry = ObservationRegistry.create()

    @Bean
    fun timeOperator(): TimeOperator = mock(TimeOperator::class.java)
  }

  @Configuration(proxyBeanMethods = false)
  class OverridingConfiguration {
    @Bean
    fun customQuartzService(): QuartzService = OVERRIDE

    companion object {
      val OVERRIDE: QuartzService = mock(QuartzService::class.java)
    }
  }
}
