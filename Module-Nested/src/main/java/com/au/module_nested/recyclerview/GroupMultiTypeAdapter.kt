package com.au.module_nested.recyclerview

import androidx.recyclerview.widget.GridLayoutManager
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

/**
 * 分组列表中表示占满整行的 span 值。
 */
const val GROUP_MULTI_TYPE_FULL_SPAN = -1

/**
 * 多类型分组列表的扁平化 item。
 */
sealed class GroupMultiTypeItem<out HEADER : Any, out CHILD : Any> : IMultiViewTypeBean {
    abstract val groupKey: String
    abstract val spanSize: Int

    /**
     * 分组标题 item。
     */
    data class Header<out HEADER : Any>(
        override val groupKey: String,
        val data: HEADER,
        val isExpanded: Boolean,
        val childCount: Int,
        override val viewType: Int,
        override val spanSize: Int = GROUP_MULTI_TYPE_FULL_SPAN,
    ) : GroupMultiTypeItem<HEADER, Nothing>()

    /**
     * 分组内容 item。
     */
    data class Child<out CHILD : Any>(
        override val groupKey: String,
        val data: CHILD,
        val childIndex: Int,
        override val viewType: Int,
        override val spanSize: Int = 1,
    ) : GroupMultiTypeItem<Nothing, CHILD>()
}

/**
 * 多类型分组列表的一组数据。
 */
class GroupMultiType<HEADER : Any, CHILD : Any>(
    val groupKey: String,
    val header: HEADER,
    val children: List<CHILD>,
    val headerViewType: Int,
    val childViewTypeProvider: (CHILD) -> Int,
    val isExpanded: Boolean = true,
    val headerSpanSize: Int = GROUP_MULTI_TYPE_FULL_SPAN,
    val childSpanSizeProvider: (CHILD) -> Int = { 1 },
) {
    /**
     * 用固定 child viewType 创建分组。
     */
    constructor(
        groupKey: String,
        header: HEADER,
        children: List<CHILD>,
        headerViewType: Int,
        childViewType: Int,
        isExpanded: Boolean = true,
        headerSpanSize: Int = GROUP_MULTI_TYPE_FULL_SPAN,
        childSpanSize: Int = 1,
    ) : this(
        groupKey = groupKey,
        header = header,
        children = children,
        headerViewType = headerViewType,
        childViewTypeProvider = { childViewType },
        isExpanded = isExpanded,
        headerSpanSize = headerSpanSize,
        childSpanSizeProvider = { childSpanSize },
    )

    /**
     * 创建只变更展开状态的新分组。
     */
    fun copyWithExpanded(isExpanded: Boolean): GroupMultiType<HEADER, CHILD> {
        return GroupMultiType(
            groupKey = groupKey,
            header = header,
            children = children,
            headerViewType = headerViewType,
            childViewTypeProvider = childViewTypeProvider,
            isExpanded = isExpanded,
            headerSpanSize = headerSpanSize,
            childSpanSizeProvider = childSpanSizeProvider,
        )
    }

    /**
     * 转换为标题 item。
     */
    fun toHeaderItem(): GroupMultiTypeItem.Header<HEADER> {
        return GroupMultiTypeItem.Header(
            groupKey = groupKey,
            data = header,
            isExpanded = isExpanded,
            childCount = children.size,
            viewType = headerViewType,
            spanSize = headerSpanSize,
        )
    }

    /**
     * 转换为内容 item。
     */
    fun toChildItems(): List<GroupMultiTypeItem.Child<CHILD>> {
        return children.mapIndexed { index, child ->
            GroupMultiTypeItem.Child(
                groupKey = groupKey,
                data = child,
                childIndex = index,
                viewType = childViewTypeProvider(child),
                spanSize = childSpanSizeProvider(child),
            )
        }
    }

    /**
     * 按当前展开状态转换为 RecyclerView 数据。
     */
    fun toVisibleItems(): List<GroupMultiTypeItem<HEADER, CHILD>> {
        val result = mutableListOf<GroupMultiTypeItem<HEADER, CHILD>>()
        result.add(toHeaderItem())
        if (isExpanded) {
            result.addAll(toChildItems())
        }
        return result
    }
}

/**
 * 多类型分组列表 Adapter，负责分组扁平化、展开收起和 Grid span 计算。
 */
abstract class GroupMultiTypeAdapter<HEADER : Any, CHILD : Any, VH : BindViewHolder<GroupMultiTypeItem<HEADER, CHILD>, *>> :
    AutoLoadMoreBindRcvAdapter<GroupMultiTypeItem<HEADER, CHILD>, VH>() {

    private val groups = mutableListOf<GroupMultiType<HEADER, CHILD>>()

    /**
     * 提交新的分组数据。
     */
    fun submitGroups(
        newGroups: List<GroupMultiType<HEADER, CHILD>>?,
        isTraditionalUpdate: Boolean = true,
    ) {
        groups.clear()
        if (!newGroups.isNullOrEmpty()) {
            groups.addAll(newGroups)
        }
        refreshVisibleItems(isTraditionalUpdate)
    }

    /**
     * 获取当前分组快照。
     */
    fun currentGroups(): List<GroupMultiType<HEADER, CHILD>> {
        return groups.toList()
    }

    /**
     * 切换某个分组的展开状态。
     */
    fun toggleGroup(groupKey: String): Boolean {
        val group = groups.find { it.groupKey == groupKey } ?: return false
        return setGroupExpanded(groupKey, !group.isExpanded)
    }

    /**
     * 设置某个分组的展开状态。
     */
    fun setGroupExpanded(groupKey: String, isExpanded: Boolean): Boolean {
        val groupIndex = groups.indexOfFirst { it.groupKey == groupKey }
        if (groupIndex < 0) return false

        val oldGroup = groups[groupIndex]
        if (oldGroup.isExpanded == isExpanded) return false

        val oldDataSize = datas.size
        val newGroup = oldGroup.copyWithExpanded(isExpanded)
        groups[groupIndex] = newGroup

        val headerPosition = findHeaderPosition(groupKey)
        if (headerPosition < 0) {
            refreshVisibleItems(true)
            return true
        }

        datas[headerPosition] = newGroup.toHeaderItem()
        notifyItemChanged(headerPosition)

        if (isExpanded) {
            val insertItems = newGroup.toChildItems()
            if (insertItems.isNotEmpty()) {
                datas.addAll(headerPosition + 1, insertItems)
                notifyItemRangeInserted(headerPosition + 1, insertItems.size)
            }
        } else {
            val removeCount = visibleChildrenCount(headerPosition, groupKey)
            if (removeCount > 0) {
                repeat(removeCount) {
                    datas.removeAt(headerPosition + 1)
                }
                notifyItemRangeRemoved(headerPosition + 1, removeCount)
            }
        }

        if (oldDataSize == datas.size) {
            onDataChanged(DataUpdateExtraInfo(headerPosition))
        } else {
            onDataChanged(DataChangeExtraInfo(oldDataSize, datas.size))
        }
        return true
    }

    /**
     * 查询某个分组是否展开。
     */
    fun isGroupExpanded(groupKey: String): Boolean {
        return groups.find { it.groupKey == groupKey }?.isExpanded ?: false
    }

    /**
     * 创建给 GridLayoutManager 使用的 spanSizeLookup。
     */
    fun createSpanSizeLookup(totalSpanCount: Int): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return getSpanSize(position, totalSpanCount)
            }
        }
    }

    /**
     * 获取指定位置的 spanSize。
     */
    fun getSpanSize(position: Int, totalSpanCount: Int): Int {
        if (totalSpanCount < 1) return 1
        if (position < 0 || position >= datas.size) return 1
        val item = datas[position]
        if (item.spanSize == GROUP_MULTI_TYPE_FULL_SPAN) return totalSpanCount
        if (item.spanSize < 1) return 1
        if (item.spanSize > totalSpanCount) return totalSpanCount
        return item.spanSize
    }

    private fun refreshVisibleItems(isTraditionalUpdate: Boolean) {
        if (isTraditionalUpdate) {
            isPlacesHolder = false
            hasMore = false
            submitTraditional(flattenGroups())
        } else {
            initDatas(flattenGroups(), false, false)
        }
    }

    private fun flattenGroups(): List<GroupMultiTypeItem<HEADER, CHILD>> {
        val result = mutableListOf<GroupMultiTypeItem<HEADER, CHILD>>()
        groups.forEach { group ->
            result.addAll(group.toVisibleItems())
        }
        return result
    }

    private fun findHeaderPosition(groupKey: String): Int {
        return datas.indexOfFirst { item ->
            item is GroupMultiTypeItem.Header<*> && item.groupKey == groupKey
        }
    }

    private fun visibleChildrenCount(headerPosition: Int, groupKey: String): Int {
        var count = 0
        var index = headerPosition + 1
        while (index < datas.size) {
            val item = datas[index]
            if (item !is GroupMultiTypeItem.Child<*> || item.groupKey != groupKey) {
                break
            }
            count++
            index++
        }
        return count
    }
}
