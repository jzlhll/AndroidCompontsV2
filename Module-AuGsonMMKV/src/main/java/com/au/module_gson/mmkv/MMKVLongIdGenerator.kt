package com.au.module_gson.mmkv

import kotlin.math.max

/**
 * 基于 MMKV 持久化的单调递增 Long ID 生成器。
 */
class MMKVLongIdGenerator(key: String) {
    private var seq: Long by MMKVLongCache(key, 0L)

    /**
     * 生成下一个单调递增的 ID。
     */
    @Synchronized
    fun next(): Long {
        val next = seq + 1
        seq = next
        return next
    }

    /**
     * 确保当前的 ID 至少不小于指定的值。
     */
    @Synchronized
    fun ensureAtLeast(minValue: Long) {
        seq = max(seq, minValue)
    }
}
