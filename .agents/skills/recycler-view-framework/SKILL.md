---
name: recycler-view-framework
description: 规定本仓库 RecyclerView 适配器、ViewHolder、多类型 Item 与加载更多在 Module-Nested 中的用法。在生成或修改 RecyclerView 相关代码时使用。
---

代码目录： Module-Nested/src/main/java/com/au/module_nested

# 编写规范
## 文件结构
Adapter：独立文件XXXAdapter.kt（放Fragment旁）
Holder：可作为Adapter内部类XXXHolder，也可独立成文件

## Bean实现
- 所有的 Adapter 数据 Bean 必须实现 `IViewTypeBean` 接口（空接口，用于标记）。
- 多类型 Item 时，数据 Bean 必须实现 `IMultiViewTypeBean` 接口，并重写 `viewType` 属性。
- 示例：
```kotlin
// 单一类型
data class MyBean(val title: String) : IViewTypeBean

// 多类型
data class MyMultiBean(override val viewType: Int, val data: String) : IMultiViewTypeBean
```

## Adapter实现
### 普通Adapter
继承BindRcvAdapter<DATA: IViewTypeBean, VH: BindViewHolder<DATA, *>>
泛型：Bean + ViewHolder
在onCreateViewHolder中，必须使用return XxxHolder(create(parent))实例化Holder；create(parent)函数利用泛型自动完成 ViewBinding 的 Inflate，无需手动LayoutInflater
数据提交：
- 普通数据更新：submitList(data, false)（第二个参数固定为false）

### 需要触底自动加载更多
使用AutoLoadMoreBindRcvAdapter或者SmartRLBindRcvAdapter替代BindRcvAdapter
- 如果放在SmartRefreshLayout里面，使用SmartRLBindRcvAdapter
数据提交：
- 初始化数据：initDatas(data, hasMore)函数提交初始数据
- 追加数据：appendDatas(data, hasMore)函数追加数据
支持differ提交：
- 实现createDiffer()方法，创建DiffCallback实例
- DiffCallback实现
  - 继承DiffCallback<DATA>，实现compareContent方法
  - compareContent方法用于比较两个数据项的内容是否相同

### 多类型Adapter
- 数据 Bean 需实现 `IMultiViewTypeBean` 接口，并定义 `viewType` 属性。
- 框架会自动根据 `IMultiViewTypeBean` 返回 viewType，**无需**手动重写 `getItemViewType`。
- 在 `onCreateViewHolder` 中根据 `viewType` 参数创建不同的 ViewHolder 实例。

## ViewHolder实现
继承BindViewHolder<Bean, VB>(binding)，传递binding参数
泛型：Bean + ViewBinding
重写bindData(bean: Bean)：必须包含super.bindData(bean)
数据访问：通过currentData获取当前绑定的数据

## 交互与刷新优化（按需实现）

### 点击事件（Init中绑定）
**默认不实现。仅当有点击需求时：**
- **位置**：**必须**在 ViewHolder 的 `init {}` 中设置监听，**严禁**在 `bindData` 中设置。
- **数据**：使用 `currentData` 属性获取当前 Item 数据（需判空）。
- **传递**：通过 Holder 构造函数接收 Lambda 回调。

### Payload 局部刷新（避免闪烁）
**默认不实现。仅当需局部更新（如选中/进度）且需避免闪烁时：**
- **用法**：
  1. 调用 `notifyItemChanged(pos, payload)`（payload 非空）。
  2. Holder 重写 `payloadsRefresh`，**必须**先调 `super`，再处理 payload。
  3. 将 UI 更新逻辑抽取为独立方法（如 `updateSelection`），供 `bindData` 和 `payloadsRefresh` 复用。

### 综合示例（按需选用）
```kotlin
class MyHolder(binding: MyBinding, val onClick: (MyBean) -> Unit) : BindViewHolder<MyBean, MyBinding>(binding) {
    init {
        // [按需] 仅有点击需求时编写
        binding.root.onClick { (currentData as? MyBean)?.let { onClick(it) } }
    }

    override fun bindData(bean: MyBean) {
        super.bindData(bean)
        updateSelection(bean) // 全量更新
    }

    // [按需] 仅有Payload刷新需求时编写
    override fun payloadsRefresh(bean: MyBean, payloads: MutableList<Any>) {
        super.payloadsRefresh(bean, payloads) // 必须调用super
        if (payloads.any { it == "refresh_select" }) updateSelection(bean) // 局部更新
    }
    
    private fun updateSelection(bean: MyBean) { /* 更新视图状态 */ }
}
```

## 其他
InViewPage2RecyclerView和InViewPageRecyclerView忽略他们的内部实现，就把他们当作普通的RecyclerView来使用。

