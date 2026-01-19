---
name: RecyclerView开发框架
description: 当生成和修改代码涉及到RecyclerView时，使用代码仓库中的开发框架，遵守它。
---

代码目录：[module_nested](Module-Nested/src/main/java/com/au/module_nested)

# 编写规范
## 文件结构
Adapter：独立文件XXXAdapter.kt（放Fragment旁）
Holder：可作为Adapter内部类XXXHolder，也可独立成文件

## Adapter实现
### 普通Adapter
继承BindRcvAdapter<DATA:Any, VH: BindViewHolder<DATA, *>>
泛型：Bean + ViewHolder
重写onCreateViewHolder：根据viewType用xxxHolder(create(parent))创建,其中create(parent)函数封装了LayoutInflater创建binding的逻辑
数据提交：
- 普通数据更新：submitList(data, false)（第二个参数固定为false）

### 需要触底自动加载更多
使用AutoLoadMoreBindRcvAdapter或者SmartRLBindRcvAdapter替代BindRcvAdapter
- 如果放在SmartRefreshLayout里面，使用SmartRLBindRcvAdapter
数据提交：
- 初始化数据：initDatas(data, hasMore)函数提交初始数据
- 追加数据：appendDatas(data, hasMore)函数追加数据
支持differ提交：
- isSupportDiffer()函数返回true
- 实现createDiffer()方法，创建DiffCallback实例
- DiffCallback实现
  - 继承DiffCallback<DATA>，实现compareContent方法
  - compareContent方法用于比较两个数据项的内容是否相同

### 多类型Adapter
- 重写getItemViewType，返回datas[position].viewType（Bean中需定义viewType字段）
- 在onCreateViewHolder中根据viewType创建不同的ViewHolder实例

## ViewHolder实现
继承BindViewHolder<Bean, VB>(binding)，传递binding参数
泛型：Bean + ViewBinding
重写bindData(bean: Bean)：必须包含super.bindData(bean)
数据访问：通过currentData获取当前绑定的数据

## 点击事件传递流程
1. 在ViewHolder的init{}中对目标View使用onClick{}监听
2. ViewHolder构造函数添加onXxxClickBlock回调参数
3. Holder通过currentData获取数据并回调onXxxClickBlock
4. 创建Adapter时传入点击事件onXxxClickBlock参数
5. Adapter在onCreateViewHolder中将onXxxClickBlock传递给Holder