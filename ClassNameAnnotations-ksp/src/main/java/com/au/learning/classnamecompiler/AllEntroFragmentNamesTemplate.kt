package com.au.learning.classnamecompiler

import com.allan.classnameanno.EntryData

/**
 * @author allan
 * @date :2024/7/3 14:11
 * @description:
 */
class AllEntryFragmentNamesTemplate : AbsCodeTemplate() {
    private val list = ArrayList<Pair<String, Int>>()
    private var autoEnterClass:String? = null
    private var autoEnterPriority = Int.MIN_VALUE

    /**
     * com.allan.androidlearning.activities.LiveDataFragment::class.java
     */
    fun insert(javaClass:String, entryData: EntryData) {
        val autoEnter = entryData.autoEnter
        if (entryData.customName.isNullOrEmpty()) {
            list.add("list.add(Pair($javaClass::class.java, ${entryData.toCreatorString()}))" to entryData.priority)
        } else {
            list.add("list.add(Pair($javaClass::class.java, ${entryData.toCreatorString()}))" to entryData.priority)
        }

        if (autoEnter && autoEnterPriority < entryData.priority) {
            autoEnterClass = "$javaClass::class.java"
            autoEnterPriority = entryData.priority
        }
    }

    fun end() : String {
        val insertCode = StringBuilder()
        list.sortBy { -it.second }
        list.forEach {
            insertCode.append(it.first).appendLine()
        }
        return codeTemplate
            .replace("//insert001", insertCode.toString())
            .replace("//insert002", autoEnterClass ?: "null")
    }

    override val codeTemplate = """
package com.allan.androidlearning

import androidx.fragment.app.Fragment
import com.allan.classnameanno.EntryData

class EntryList {
    fun getEntryList(): List<Pair<Class<out Fragment>, EntryData>> {
        val list = ArrayList<Pair<Class<out Fragment>, EntryData>>()
        //insert001
        return list
    }

    fun getAutoEnterClass() : Class<out Fragment>? {
        return //insert002
    }
}
    """.trimIndent()
}