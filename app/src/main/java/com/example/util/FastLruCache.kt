package com.example.util

/**
 * Fast, thread-safe, pure-Kotlin LRU Cache.
 * Compatible with both Android runtime and JVM unit test environments
 * (unlike android.util.LruCache which throws "Method not mocked" in local unit tests).
 */
class FastLruCache<K, V>(private val maxSize: Int) {

    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun evictAll() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}
