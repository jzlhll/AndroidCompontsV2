---
name: 布局xml与Fragment生成
description: 当涉及到Fragment生成，Layout xml布局创建修改，和dimen，style等属性使用时，遵守它。
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
文案：用tools:text编写(记得引入xmlns:tools)

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

# Fragment
继承BindingFragment<XXXBinding>
重写onBindingCreated
点击用onClick扩展
标题栏：默认用YourToolbarInfo.Defaults，自定义用YourToolbarInfo.Yours
不要给布局的根控件添加id，使用binding.root即可(无需强转)

# RecyclerView
不主动写Adapter(除非允许)
如果允许参考规则SKILL: [RecyclerView开发框架]