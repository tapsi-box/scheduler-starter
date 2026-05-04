package box.tapsi.libs.scheduler.scheduler.aop.interceptors

import box.tapsi.libs.utilities.time.TimeOperator
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RetryJobInterceptorTest {
  private lateinit var interceptor: RetryJobInterceptor

  @BeforeEach
  fun setUp() {
    interceptor = RetryJobInterceptor(mock(TimeOperator::class.java))
  }

  // region fixed delay

  @Test
  fun `should return FIXED_OFFSET_RETRY_MILLIS when exponential backoff is disabled and no fix delay is set`() {
    // given
    val retryable = retryable(exponentialBackoff = false, backOffFixDelay = -1L)

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 0)

    // verify
    assertEquals(RetryJobInterceptor.FIXED_OFFSET_RETRY_MILLIS, delay)
  }

  @Test
  fun `should return backOffFixDelay when exponential backoff is disabled and fix delay is configured`() {
    // given
    val fixDelay = 5 * 60 * 1000L
    val retryable = retryable(exponentialBackoff = false, backOffFixDelay = fixDelay)

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 0)

    // verify
    assertEquals(fixDelay, delay)
  }

  @Test
  fun `should return same fix delay on every retry attempt when exponential backoff is disabled`() {
    // given
    val fixDelay = 10 * 60 * 1000L
    val retryable = retryable(exponentialBackoff = false, backOffFixDelay = fixDelay)

    // when
    val delayAttempt0 = interceptor.computeDelayMillis(retryable, retryCount = 0)
    val delayAttempt3 = interceptor.computeDelayMillis(retryable, retryCount = 3)

    // verify
    assertEquals(fixDelay, delayAttempt0)
    assertEquals(fixDelay, delayAttempt3)
  }

  // endregion

  // region exponential backoff

  @Test
  fun `should return minDelay on first attempt when exponential backoff is enabled`() {
    // given
    val minDelay = 30 * 60 * 1000L
    val retryable = retryable(exponentialBackoff = true, backOffMinDelay = minDelay, backOffFactor = 2.0)

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 0)

    // verify
    assertEquals(minDelay, delay)
  }

  @Test
  fun `should multiply delay by factor on each retry attempt`() {
    // given
    val minDelay = 60 * 1000L
    val factor = 2.0
    val retryable = retryable(exponentialBackoff = true, backOffMinDelay = minDelay, backOffFactor = factor)

    // when
    val delayAttempt1 = interceptor.computeDelayMillis(retryable, retryCount = 1)
    val delayAttempt2 = interceptor.computeDelayMillis(retryable, retryCount = 2)
    val delayAttempt3 = interceptor.computeDelayMillis(retryable, retryCount = 3)

    // verify
    assertEquals((minDelay * 2.0).toLong(), delayAttempt1)
    assertEquals((minDelay * 4.0).toLong(), delayAttempt2)
    assertEquals((minDelay * 8.0).toLong(), delayAttempt3)
  }

  @Test
  fun `should cap delay at maxDelay when computed delay exceeds it`() {
    // given
    val minDelay = 60 * 1000L
    val maxDelay = 5 * 60 * 1000L
    val retryable = retryable(
      exponentialBackoff = true,
      backOffMinDelay = minDelay,
      backOffMaxDelay = maxDelay,
      backOffFactor = 2.0,
    )

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 10)

    // verify
    assertEquals(maxDelay, delay)
  }

  @Test
  fun `should use DEFAULT_BACKOFF_FACTOR when backOffFactor is not set`() {
    // given
    val minDelay = 60 * 1000L
    val retryable = retryable(exponentialBackoff = true, backOffMinDelay = minDelay, backOffFactor = -1.0)

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 1)

    // verify
    assertEquals((minDelay * RetryJobInterceptor.DEFAULT_BACKOFF_FACTOR).toLong(), delay)
  }

  @Test
  fun `should use FIXED_OFFSET_RETRY_MILLIS as minDelay fallback when backOffMinDelay is not set`() {
    // given
    val retryable = retryable(exponentialBackoff = true, backOffMinDelay = -1L, backOffFactor = 2.0)

    // when
    val delay = interceptor.computeDelayMillis(retryable, retryCount = 0)

    // verify
    assertEquals(RetryJobInterceptor.FIXED_OFFSET_RETRY_MILLIS, delay)
  }

  @Test
  fun `should produce flat retry delay when factor is 1 and min equals max delay`() {
    // given
    val flatDelay = 30 * 60 * 1000L
    val retryable = retryable(
      exponentialBackoff = true,
      backOffMinDelay = flatDelay,
      backOffMaxDelay = flatDelay,
      backOffFactor = 1.0,
    )

    // when
    val delayAttempt0 = interceptor.computeDelayMillis(retryable, retryCount = 0)
    val delayAttempt5 = interceptor.computeDelayMillis(retryable, retryCount = 5)

    // verify
    assertEquals(flatDelay, delayAttempt0)
    assertEquals(flatDelay, delayAttempt5)
  }

  // endregion

  private fun retryable(
    exponentialBackoff: Boolean = false,
    backOffFixDelay: Long = -1L,
    backOffMinDelay: Long = -1L,
    backOffMaxDelay: Long = -1L,
    backOffFactor: Double = -1.0,
  ): ReactiveRetryable = mock(ReactiveRetryable::class.java).also {
    `when`(it.exponentialBackoff).thenReturn(exponentialBackoff)
    `when`(it.backOffFixDelay).thenReturn(backOffFixDelay)
    `when`(it.backOffMinDelay).thenReturn(backOffMinDelay)
    `when`(it.backOffMaxDelay).thenReturn(backOffMaxDelay)
    `when`(it.backOffFactor).thenReturn(backOffFactor)
  }
}
