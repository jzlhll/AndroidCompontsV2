package com.au.module_android.selectlist

/**
 * @author allan
 * @date :2024/7/29 11:00
 * @description: 每一份的数据。比如实现equal。
 */
abstract class SimpleItem {
    abstract val itemName:String
    abstract val onItemClick : ()->Unit

    override fun equals(other: Any?): Boolean {
        return other is SimpleItem && itemName == other.itemName
    }

    override fun hashCode(): Int {
        val result = itemName.hashCode()
        return result
    }
}