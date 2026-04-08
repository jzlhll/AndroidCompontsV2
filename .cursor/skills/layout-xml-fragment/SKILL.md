---
name: layout-xml-fragment
description: 规定 Android 布局 XML、dimen/style、Fragment 与 Figma 联动时的控件选型、阴影、样式、间距、ID 与字符串规范。在新建或修改 layout、生成 Fragment、从 Figma 落地界面、调整 dimen/style 时使用。
---

# 布局 XML 与 Fragment 生成

## 何时应用

- 新建或修改 `layout` XML、dimen、style
- 编写或改动 `Fragment`（含 ViewBinding）
- 与 Figma 对照实现界面或 shadow 等属性映射

# 控件

全路径 `com.au.module_androidui.widget.XXX`

- 文本：`CustomFontText`
- 按钮：`CustomButton`
- 输入框：`CustomEditText`
- 圆角容器：`BgBuildXXXLayout`
- 圆角背景：`BgBuildView`
- 流式布局：`FlowLayout`（属性 `flChildSpacing`、`flRowSpacing`）

# 容器

- 有圆角：`BgBuildXXXLayout`，相关属性：`backgroundNormal`、`cornerRadius` 等
- 无圆角：常规布局（如 `ConstraintLayout`）

## 阴影处理

- 默认不添加阴影。
- 若我明确要求阴影：追加下列属性（数值不改），并将父控件 `clipChildren`、`clipToPadding` 设为 `false`：

  ```xml
  app:shadowBlur="8dp"
  app:shadowColor="@color/i8o_color_shadow_default"
  app:shadowOffsetY="4dp"
  app:backgroundNormal="#ffffff"
  ```

- 联动 Figma 时，shadow 对应以下 5 个属性：

  ```xml
  <attr name="shadowColor" />
  <attr name="shadowOffsetX" />
  <attr name="shadowOffsetY" />
  <attr name="shadowBlur" />
  <attr name="shadowSpread" />
  ```

# 样式

- 主文件：[styles.xml](../../../Module-AndroidColor/src/main/res/values/styles.xml)
- 文本：`StyleI8o14sp`（常规）、`StyleI8o14Desc`（灰色描述）等
- 输入框：`StyleBlankEditText`
- 按钮：`StyleButtonPrimary`（常规）、`StyleButtonWarn`（警告）等
- 与 Figma 联动或我要求时，可新增 style 并引用
- **必须使用 style**，不得直接在控件上写 `textColor`、`textSize`

# 图片圆角

使用 `com.google.android.material.imageview.ShapeableImageView`  
引用：`app:shapeAppearanceOverlay="@style/shape_roundXdp_Style"`  
样式定义：[images_styles.xml](../../../Module-AndroidUiEx/src/main/res/values/images_styles.xml)

# 边距

- 靠边：`@dimen/ui_padding_edge`
- 其他间距（>10dp）：取 3 的倍数（12、15、24 等）

# ID 命名

- 文本：`xxxTv`
- 输入框：`xxxEdit`
- 按钮：`xxxBtn`
- 图片：`xxxImg`
- 大容器：`xxxHost`
- 小容器：`xxxLayout`
- RecyclerView：`xxxRcv`

# 文字

文案写入 `strings.xml`，布局中用 `@string/xxx` 引用。

# Fragment

- 继承 `BindingFragment<XXXBinding>`
- 重写 `onBindingCreated`
- 点击使用 `onClick` 扩展
- 多 View 共用同一逻辑：定义为 `(View) -> Unit`，各 View 传入同一 lambda
- 标题栏：默认 `YourToolbarInfo.Defaults`，自定义 `YourToolbarInfo.Yours`
- 根控件不加 id，使用 `binding.root`（无需强转）

# RecyclerView

不主动写 Adapter（除非我允许）。允许时参考 [RecyclerView 开发框架](../recycler-view-framework/SKILL.md)。

# 布局要求

- 最外层通常为 `ConstraintLayout`
- 层级不超过 3 层时，可用 `ConstraintLayout` + `LinearLayout` + `RelativeLayout` 控制深度
- 平铺布局时不要增加仅作「假背景」的空 View
