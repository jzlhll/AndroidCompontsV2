# 实现下拉刷新RecyclerView功能

## 1. 创建Item布局文件

* 创建 `layout/item_refresh1.xml`

* 使用 `ConstraintLayout` 作为根容器

* 添加 `CustomFontText` 显示文本，应用 `StyleAuTextNormal` 样式

* 添加底部 `View` 作为1dp分割线，颜色使用 `color_line`

## 2. 实现Refresh1Adapter

* 修改 `Refresh1Adapter.kt`，添加内部类 `Refresh1ViewHolder`

* `Refresh1Adapter` 继承 `BindRcvAdapter<Bean, Refresh1Adapter.Refresh1ViewHolder>`

* 重写 `onCreateViewHolder` 方法，创建 `Refresh1ViewHolder`

* `Refresh1ViewHolder` 继承 `BindViewHolder<Bean, ItemRefresh1Binding>`

* 重写 `bindData` 方法，设置文本内容

## 3. 配置Refresh1Fragment

* 在 `onBindingCreated` 方法中配置RecyclerView

* 设置LayoutManager为LinearLayoutManager

* 初始化Adapter并设置给RecyclerView

* 添加模拟数据，调用Adapter的 `submitList` 方法

* 配置下拉刷新监听器

