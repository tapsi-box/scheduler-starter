package box.tapsi.libs.scheduler.scheduler

fun SchedulingInstruction.getCompositeJobId(): String = retriedCount?.let {
  return@let "${jobId}_$it"
} ?: jobId
