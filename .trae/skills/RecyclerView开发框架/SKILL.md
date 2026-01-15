---
name: RecyclerView开发框架
description: 当生成和修改代码涉及到RecyclerView时，使用代码仓库中的开发框架，遵守如下规则。
---

[module_nested](Module-Nested/src/main/java/com/au/module_nested)：
# 文件结构
Adapter：独立文件XXXAdapter.kt（放Fragment旁）
Holder：Adapter内部类XXXHolder

# Adapter实现
继承BindRcvAdapter<DATA:Any, VH: BindViewHolder<DATA, *>>
泛型：Bean + ViewHolder
重写onCreateViewHolder：根据viewType用xxxHolder(create(parent))创建
提交数据：submitList(data, false)（第二个参数固定为false）
多类型：重写getItemViewType，返回datas[position].viewType（Bean中需定义viewType字段）

# Holder实现
继承BindViewHolder<Bean, VB>
泛型：Bean + ViewBinding
重写bindData(bean: Bean)：必须包含super.bindData(bean)
点击事件：在init{}中用onClick扩展；构造函数添加onXxxClickBlock参数
数据传递：currentData?.let { data -> onXxxClickBlock.invoke(data) }

# 点击事件传递流程
创建Adapter时传入点击事件lambda
Adapter在onCreateViewHolder中将lambda传递给Holder
Holder通过currentData获取数据并回调lambda