# Tapsi Scheduler Starter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.23-blue.svg)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven Central](https://img.shields.io/maven-central/v/box.tapsi.libs/scheduler-starter)](https://search.maven.org/artifact/box.tapsi.libs/scheduler-starter)

Reactive Job Scheduling Library for Spring Boot Applications

## Overview

Tapsi Scheduler Starter is a comprehensive Kotlin library that provides reactive job scheduling capabilities for Spring
Boot applications. Built on top of Quartz scheduler with Reactor integration, it offers both regular and cron-based
scheduling with automatic configuration, retry mechanisms, and comprehensive monitoring.

## Features

### ⏰ Job Scheduling

- **Regular scheduling** for one-time and delayed jobs
- **Cron scheduling** for recurring jobs with flexible expressions
- **Reactive programming** with Mono/Flux support
- **Automatic job discovery** and scheduling at startup

### 🔄 Retry Mechanisms

- **AOP-based retry** with configurable retry policies
- **Exponential backoff** support
- **Custom retry strategies** for different job types
- **Retry metrics** and monitoring

### 📊 Monitoring & Metrics

- **Quartz metrics integration** with Micrometer
- **Job execution monitoring** with active job listeners
- **Misfire detection** and handling
- **Performance metrics** for job execution

### 🚀 Spring Boot Integration

- **Auto-configuration** for seamless setup
- **Conditional scheduling** based on properties
- **Bean exclusion** for selective job scheduling
- **Quartz integration** with Spring Boot

### 🛠️ Advanced Features

- **Job store management** with persistent storage
- **Trigger management** with dynamic rescheduling
- **Job grouping** and organization
- **Exception handling** with custom exceptions

## Installation

### Maven

```xml

<dependency>
    <groupId>box.tapsi.libs</groupId>
    <artifactId>scheduler-starter</artifactId>
    <version>0.9.0</version>
</dependency>
```

## Quick Start

### 1. Add Dependency

Include the library in your Spring Boot project's dependencies.

### 2. Auto-Configuration

The library automatically configures Quartz scheduler and necessary beans when included in your classpath.

### 3. Create Scheduled Jobs

```kotlin
import box.tapsi.libs.scheduler.scheduler.schedulers.*

// Regular scheduler for one-time jobs
@Component
class EmailNotificationScheduler(
  private val schedulerService: SchedulerService
) : RegularScheduler(schedulerService) {

  override fun execute(jobStore: JobStore?): Mono<Void> {
    return Mono.fromRunnable {
      // Send email notification logic
      println("Sending email notification...")
    }.then()
  }

  override fun getJobGroup(): JobGroup = JobGroup("notifications")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup("email-triggers")
}

// Cron scheduler for recurring jobs
@Component
class DataCleanupScheduler(
  private val schedulerService: SchedulerService
) : CronScheduler(schedulerService) {

  override fun getCronExpression(): String = "0 0 2 * * ?" // Daily at 2 AM

  override fun execute(jobStore: JobStore?): Mono<Void> {
    return Mono.fromRunnable {
      // Data cleanup logic
      println("Cleaning up old data...")
    }.then()
  }

  override fun getJobGroup(): JobGroup = JobGroup("maintenance")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup("cleanup-triggers")
}
```

## Usage Examples

### Regular Job Scheduling

```kotlin
@Service
class TaskService(
  private val schedulerService: SchedulerService
) {

  fun scheduleTask(taskId: String, delayMinutes: Long) {
    val fireTime = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES)

    val instruction = SchedulingInstruction.Regular(
      fireTimestamp = fireTime,
      scheduler = TaskScheduler::class,
      jobStore = JobStore(mapOf("taskId" to taskId)),
      jobId = "task-$taskId",
      retriedCount = 3,
      jobGroup = JobGroup("tasks"),
      triggerGroup = TriggerGroup("task-triggers")
    )

    schedulerService.scheduleRegularJob(instruction)
      .subscribe(
        { println("Task scheduled successfully") },
        { error -> println("Failed to schedule task: $error") }
      )
  }
}
```

### Cron Job Scheduling

```kotlin
@Component
class ReportGenerationScheduler(
  private val schedulerService: SchedulerService
) : CronScheduler(schedulerService) {

  override fun getCronExpression(): String = "0 0 9 * * MON-FRI" // Weekdays at 9 AM

  override fun execute(jobStore: JobStore?): Mono<Void> {
    return Mono.fromRunnable {
      // Generate daily reports
      generateReports()
    }.then()
  }

  override fun getJobGroup(): JobGroup = JobGroup("reports")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup("report-triggers")

  private fun generateReports() {
    // Report generation logic
  }
}
```

### Job Management

```kotlin
@Service
class JobManagementService(
  private val schedulerService: SchedulerService
) {

  fun cancelJob(jobId: String) {
    schedulerService.deleteJob(jobId, JobGroup("tasks"))
      .subscribe(
        { println("Job cancelled successfully") },
        { error -> println("Failed to cancel job: $error") }
      )
  }

  fun rescheduleJob(triggerId: String, newCronExpression: String) {
    val newTrigger = Trigger.Cron(
      triggerId = triggerId,
      cronExpression = newCronExpression,
      triggerGroup = TriggerGroup("task-triggers")
    )

    schedulerService.reschedule(triggerId, newTrigger)
      .subscribe(
        { println("Job rescheduled successfully") },
        { error -> println("Failed to reschedule job: $error") }
      )
  }

  fun getActiveTriggers(): Flux<Trigger> {
    return schedulerService.getTriggers(TriggerGroup("task-triggers"))
  }
}
```

### Retry Configuration

```kotlin
@Component
class RetryableTaskScheduler(
  private val schedulerService: SchedulerService
) : RegularScheduler(schedulerService) {

  override fun execute(jobStore: JobStore?): Mono<Void> {
    return Mono.fromRunnable {
      // Task that might fail and need retry
      performUnreliableOperation()
    }.then()
  }

  override fun getRetriedCount(jobStore: JobStore?): Int = 5 // Max 5 retries

  override fun getJobGroup(): JobGroup = JobGroup("retryable-tasks")
  override fun getTriggerGroup(): TriggerGroup = TriggerGroup("retry-triggers")

  private fun performUnreliableOperation() {
    // Operation that might fail
  }
}
```

## Configuration

### Scheduler Properties

```yaml
box:
  libs:
    scheduler:
      quartz:
        enabled: true  # Enable Quartz integration
      cron-job:
        scheduling-enabled: true  # Enable auto-scheduling of cron jobs
        scheduling-excludes: # Exclude specific cron jobs from auto-scheduling
          - "maintenanceScheduler"
          - "backupScheduler"
```

### Quartz Configuration

```yaml
spring:
  quartz:
    job-store-type: jdbc  # Use database for job persistence
    properties:
      org:
        quartz:
          scheduler:
            instanceName: MyScheduler
            instanceId: AUTO
          jobStore:
            class: org.quartz.impl.jdbcjobstore.JobStoreTX
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
            tablePrefix: QRTZ_
            useProperties: false
          threadPool:
            class: org.quartz.simpl.SimpleThreadPool
            threadCount: 10
            threadPriority: 5
```

## Testing

The library includes comprehensive test coverage and provides test utilities:

```kotlin
@SpringBootTest
class SchedulerServiceTest {

  @Autowired
  private lateinit var schedulerService: SchedulerService

  @Test
  fun `should schedule regular job successfully`() {
    val fireTime = Instant.now().plus(1, ChronoUnit.MINUTES)
    val instruction = SchedulingInstruction.Regular(
      fireTimestamp = fireTime,
      scheduler = TestScheduler::class,
      jobStore = JobStore(),
      jobId = "test-job",
      retriedCount = null,
      jobGroup = JobGroup("test"),
      triggerGroup = TriggerGroup("test-triggers")
    )

    StepVerifier.create(schedulerService.scheduleRegularJob(instruction))
      .verifyComplete()
  }

  @Test
  fun `should handle cron job scheduling`() {
    val instruction = SchedulingInstruction.Cron(
      fireTimestamp = null,
      cronExpression = "0 0 12 * * ?",
      scheduler = TestCronScheduler::class,
      jobStore = JobStore(),
      jobId = "test-cron-job",
      retriedCount = null,
      jobGroup = JobGroup("test"),
      triggerGroup = TriggerGroup("test-triggers")
    )

    StepVerifier.create(schedulerService.scheduleCronJob(instruction))
      .verifyComplete()
  }
}
```

## Contributing

We welcome contributions! Please see our contributing guidelines:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

### Development Setup

```bash
# Clone the repository
git clone https://github.com/tapsi-box/scheduler-starter.git

# Navigate to project directory
cd scheduler-starter

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run code quality checks
./gradlew detekt
./gradlew spotlessCheck
```

## Code Quality

This project maintains high code quality standards:

- **Kotlin** with strict compiler options
- **Detekt** for static code analysis
- **Spotless** for code formatting
- **Comprehensive testing** with JUnit 5
- **Spring Boot Test** integration
- **Reactor Test** for reactive testing

## Dependencies

### Core Dependencies

- **Spring Boot 3.5.5** - Auto-configuration support
- **Spring Boot Starter Quartz 3.5.5** - Quartz integration
- **Reactor Core 3.7.9** - Reactive programming support
- **Reactor Core Micrometer 1.2.9** - Metrics integration
- **Tapsi Metrics Core 1.0.1** - Custom metrics support
- **Tapsi Utilities Starter 0.9.1** - Common utilities
- **ProjectReactor Retry AOP 2.0.0-RC2** - Retry mechanisms

### Test Dependencies

- **JUnit 5** - Testing framework
- **Spring Boot Test** - Integration testing
- **Reactor Test** - Reactive testing utilities

## Version Compatibility

| Library Version | Spring Boot | Kotlin | Java |
|-----------------|-------------|--------|------|
| 0.0.1-SNAPSHOT  | 3.5.x       | 1.9.23 | 21+  |

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/tapsi-box/scheduler-starter/issues)
- **Discussions**: [GitHub Discussions](https://github.com/tapsi-box/scheduler-starter/discussions)
- **Documentation**: [Project Wiki](https://github.com/tapsi-box/scheduler-starter/wiki)

## Authors

- **Mahdi Bohloul** - [@mahdibohloul](https://github.com/mahdibohloul/)

## Acknowledgments

- Spring Boot team for the excellent framework
- Quartz team for the robust scheduling engine
- Reactor team for reactive programming support
- All contributors and users of this library

---

**Made with ❤️ by the Tapsi team**
