package box.tapsi.libs.scheduler.scheduler

interface JobGroup {
  val value: String

  companion object {
    fun fromString(value: String): JobGroup = object : JobGroup {
      override val value: String = value
      override fun toString(): String = value
    }
  }
}
