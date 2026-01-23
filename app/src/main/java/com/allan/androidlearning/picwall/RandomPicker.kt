package com.allan.androidlearning.picwall

import kotlin.random.Random

/**
 * 随机既定类
 * 从列表中随机取元素，取完后再从头随机
 */
class RandomPicker<T>(private val dataList: List<T>) {
    
    private val mRemainingIndices = ArrayList<Int>()
    
    init {
        reset()
    }
    
    /**
     * 随机取一个元素
     * @return 随机元素，如果列表为空则返回null
     */
    fun next(): T {
        if (mRemainingIndices.isEmpty()) {
            reset()
        }
        
        val randomIndex = Random.nextInt(mRemainingIndices.size)
        val dataIndex = mRemainingIndices.removeAt(randomIndex)
        return dataList[dataIndex]
    }
    
    /**
     * 重置剩余索引
     */
    private fun reset() {
        mRemainingIndices.clear()
        mRemainingIndices.addAll(dataList.indices)
    }
}
