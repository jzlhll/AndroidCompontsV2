---
name: 布局xml与Fragment生成
description: 当涉及到Fragment生成，Layout布局创建与修改，联动figma生成layout，dimen，style等属性使用时，遵守它。
---

# 控件

全路径 com.au.module_androidui.widget.XXX
文本：CustomFontText
按钮：CustomButton
输入框：CustomEditText
圆角容器：BgBuildXXXLayout
圆角背景：BgBuildView
流式布局：FlowLayout (属性flChildSpacing, flRowSpacing)

# 容器
有圆角：BgBuildXXXLayout, 相关属性：backgroundNormal, cornerRadius等
无圆角：常规布局(如ConstraintLayout)

## 阴影处理
- 默认不添加阴影；

- 如果我要求添加阴影效果：

  则添加如下内容，数值不做修改，并修改父控件属性clipChildren和clipToPadding为false：
  ```xml
  app:shadowBlur="8dp"
  app:shadowColor="@color/i8o_color_shadow_default"
  app:shadowOffsetY="4dp"
  app:backgroundNormal="#ffffff"
  ```

- [联动figma]需注意，转变为以下5个shadow相关属性：
  ```xml
  <attr name="shadowColor" />
  <attr name="shadowOffsetX" />
  <attr name="shadowOffsetY" />
  <attr name="shadowBlur" />
  <attr name="shadowSpread" />
  ```

# 样式

[styles.xml](../../../Module-AndroidColor/src/main/res/values/styles.xml)
文本：StyleI8o14sp(常规), StyleI8o14Desc(灰色描述)等等
输入框：StyleBlankEditText
按钮：StyleButtonPrimary(常规), StyleButtonWarn(警告)等
必要时增加：如果与figma联动或者我要求时，追加新的style并引用
注意：必须使用style，不得直接添加textColor，textSize属性

# 图片圆角
使用 `com.google.android.material.imageview.ShapeableImageView`
样式引用：`app:shapeAppearanceOverlay="@style/shape_roundXdp_Style"`
样式定义：[images_styles.xml](../../../Module-AndroidUiEx/src/main/res/values/images_styles.xml)

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

# 文字
识别的文字，添加到strings.xml中，并在layout控件上@string/xxx引入。

# Fragment
继承BindingFragment<XXXBinding>
重写onBindingCreated
点击用onClick扩展
多个View共用同一点击逻辑时，定义为`(View)->Unit`，然后对各个View直接`onClick(该lambda)`传入
标题栏：默认用YourToolbarInfo.Defaults，自定义用YourToolbarInfo.Yours
不要给布局的根控件添加id，使用binding.root即可(无需强转)

# RecyclerView
不主动写Adapter(除非允许)
如果允许参考规则SKILL: [RecyclerView开发框架](../基础架构_RecyclerView开发框架/SKILL.md)

# 布局要求
- 最外层通常是ConstraintLayout
- 如果不超过3层，使用ConstraintLayout+LinearLayout+RelativeLayout来控制整个layout层级不超过3层
- 平铺布局时，不要追加一个模拟layout的空背景View
