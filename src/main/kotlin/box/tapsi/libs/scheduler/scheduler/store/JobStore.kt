package box.tapsi.libs.scheduler.scheduler.store

import jakarta.annotation.PreDestroy

class JobStore {
  private val store: MutableMap<String, Any>

  constructor() {
    this.store = mutableMapOf()
  }

  constructor(store: MutableMap<String, Any>) {
    this.store = store
  }

  fun put(key: String, value: Any) {
    store[key] = value
  }

  fun get(key: String): Any? = store[key]

  fun contains(key: String): Boolean = store.containsKey(key)

  fun getString(key: String): String? = get(key)?.let {
    return@let requireNotNull(it as? String ?: it.toString()) {
      "Value for key $key is not a string"
    }
  }

  fun getInt(key: String): Int? = get(key)?.let {
    return@let requireNotNull(it as? Int ?: it.toString().toIntOrNull()) {
      "Value for key $key is not an integer"
    }
  }

  fun getAll(): Map<String, Any> = store

  fun remove(key: String) {
    store.remove(key)
  }

  @PreDestroy
  fun destroy() {
    store.clear()
  }
}
