package com.au.module_nested.recyclerview

/**
 * 用来标识数据Bean
 */
interface IViewTypeBean

/*
参考代码如下：
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<HomeRcvBean, *> {
    return when (viewType) {
        HomeRcvBean.VIEW_TYPE_MARKUP -> {
            HomeRcvMarkupViewHolder(create(parent))
        }
        HomeRcvBean.VIEW_TYPE_TITLE -> {
            HomeRcvTitleViewHolder(create(parent))
        }
        HomeRcvBean.VIEW_TYPE_ITEM -> {
            HomeRcvItemViewHolder(create(parent))
        }

        HomeRcvBean.VIEW_TYPE_HEAD -> HomeRcvHeadViewHolder(this, create(parent))
    }

 */
/**
 * 如果你是多ViewType的Rcv，则继承它
 */
interface IMultiViewTypeBean : IViewTypeBean{
    val viewType:Int
}