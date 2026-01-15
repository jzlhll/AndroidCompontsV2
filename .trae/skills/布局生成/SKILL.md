---
name: 布局生成
description: 当涉及到Layout xml布局和dimen，style等属性使用时使用该规则。
---

# 控件
全路径 com.au.module_androidui.widget.XXX
文本：CustomFontText (全称标签)
按钮：CustomButton
输入框：CustomEditText
圆角容器：BgBuildXXXLayout
圆角背景：BgBuildView
流式布局：FlowLayout (属性flChildSpacing, flRowSpacing)

# 容器
有圆角：BgBuildXXXLayout, 相关属性：backgroundNormal, cornerRadius
无圆角：常规布局(如ConstraintLayout)
有阴影：CardView包裹

# 样式
[styles.xml](Module-AndroidColor/src/main/res/values/styles.xml)
文本：StyleAuTextNormal(常规), StyleAuTextNormalDesc(灰色描述)等等
输入框：StyleBlankEditText
按钮：StyleButtonPrimary(常规), StyleButtonWarn(警告)等
图片文案：用tools:text

# 边距
靠边间距：@dimen/ui_padding_edge
其他间距(>10dp)：3的倍数(12,15,24等)

# ID命名
文本：xxxTv
输入框：xxxEdit
按钮：xxxBtn
图片：xxxImg
大容器：xxxHost
小容器：xxxLayout
RecyclerView：xxxRcv

# BindingFragment
继承BindingFragment<XXXBinding>
重写onBindingCreated
点击用onClick扩展

# RecyclerView
不主动写Adapter(除非允许)
如果允许参考规则SKILL: [RecyclerView开发框架]