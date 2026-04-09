# PicStack 堆叠相册实现计划

## Summary

- 在 `PicStackFragment` 中复用 `PicWallViewModel` 的本地媒体查询能力，请求媒体权限后读取前 N 个图片数据，默认 `10` 个。
- 以 `5` 张可见卡片形成堆叠效果，显示在 `fragment_pic_stack.xml` 的 `stackLayout` 区域。
- 每张卡片由一个自定义组合 View 承载：外层为固定外框图占位资源，内层为按图片真实宽高比适配的图片 `ImageView`，并保持居中嵌套。
- 仅最上层卡片可拖拽，支持向任意方向滑出并消失；消失后剩余卡片前移，新卡从数据源中补入，直到前 N 个数据耗尽。

## Current State Analysis

- `app/src/main/java/com/allan/androidlearning/picwall/PicStackFragment.kt`
  - 当前为空 Fragment，尚未接入权限、ViewModel、媒体数据观察与任何 UI 行为。
- `app/src/main/res/layout/fragment_pic_stack.xml`
  - 已存在 `switchBtn`、`stackLayout`、`rcv` 三块占位区域。
  - 本次仅处理 `stackLayout` 的堆叠交互，`switchBtn` 与 `rcv` 保留但不接逻辑。
- `app/src/main/java/com/allan/androidlearning/picwall/PicWallViewModel.kt`
  - 已能通过 `RequestLocalAction` 查询媒体库，并通过 `localMediaState` 返回 `List<UriParsedInfo>`。
- `Module-AndroidCommon/src/main/java/com/au/module_android/utilsmedia/UriParsedInfo.kt`
  - 当前数据只包含 `uri`、文件信息等，不包含图片宽高，因此内层图比例不能直接在拿到列表时确定。
- 仓库当前不存在 `draw1`、`draw2` 一类现成外框资源。
  - 你已确认本次先使用占位 `shape` 外框资源。
- 仓库已有 Glide 扩展：
  - `Module-AndroidCommon/src/main/java/com/au/module_android/glide/GlideUtil.kt`
  - 可在图片卡片中使用 `glideSetAny` 加载本地 `Uri`。

## Proposed Changes

### 1. Fragment 接入数据与权限

- 修改 `app/src/main/java/com/allan/androidlearning/picwall/PicStackFragment.kt`
  - 复用 `PicWallFragment` 的媒体权限申请方式。
  - 通过 `viewModels<PicWallViewModel>()` 获取 ViewModel。
  - 监听页面 `localMediaState`，成功后只保留图片类型数据，并截取前 N 个，默认 `10` 个。
  - 将结果传给堆叠容器，容器内部只展示前 `5` 个可见位。
  - 权限被拒绝时沿用现有 Toast 提示风格。

### 2. 用自定义堆叠容器替换 `stackLayout`

- 修改 `app/src/main/res/layout/fragment_pic_stack.xml`
  - 保持现有整体结构、`switchBtn` 与 `rcv` 不动。
  - 将当前 `stackLayout` 从普通 `ConstraintLayout` 调整为自定义堆叠容器，继续保留同名 `id`，避免 Fragment 绑定改动过大。
  - 保留 `clipChildren=false` / `clipToPadding=false` 的思路，便于旋转与拖拽动画展示。

- 新增 `app/src/main/java/com/allan/androidlearning/picwall/PicStackLayout.kt`
  - 作为 `ViewGroup` 或 `FrameLayout` 风格自定义容器，负责：
  - 维护数据源队列、当前可见卡片列表、下一张补位索引。
  - 创建并复用 `PicStackCardView` 子项。
  - 控制可见数量上限为 `5`。
  - 将最上层卡片置于最高层级，仅给最上层绑定拖拽逻辑。
  - 顶层卡片移除后，重算剩余 4 张的层级、角度与位置，并补入下一张。

### 3. 新增单卡自定义 View

- 新增 `app/src/main/res/layout/view_pic_stack_card.xml`
  - 使用一个根容器包裹两个 `ImageView`：
  - 外层 `ImageView` 用于显示占位外框资源。
  - 内层 `ImageView` 用于显示真实图片，并始终居中。

- 新增 `app/src/main/java/com/allan/androidlearning/picwall/PicStackCardView.kt`
  - 封装单卡展示逻辑。
  - 对外暴露设置外框参数与图片数据的方法。
  - 使用 Glide 扩展加载 `UriParsedInfo.uri`。
  - 在图片资源真正加载完成后，根据 `Drawable.intrinsicWidth / intrinsicHeight` 计算内层图的目标宽高。
  - 计算规则：
  - 先以外层卡片可用内容区域为上限。
  - 再按图片真实宽高比进行等比缩放。
  - 最终让内层图完整落在外框内并保持居中，不裁切、不拉伸。
  - 外框边距不写死在布局结构里，而是由卡片配置动态控制，便于后续从占位 `shape` 过渡到不同边框宽度资源。

- 新增 `app/src/main/res/drawable/bg_pic_stack_frame_placeholder.xml`
  - 作为本次外框占位资源。
  - 计划中保留“边框宽度可配置”的接口，后续替换为 `draw1/draw2` 一类真实资源时不需要改堆叠逻辑。

### 4. 堆叠角度与位置计算

- 在 `PicStackLayout.kt` 中实现卡片布局策略：
  - 第 1 张角度固定 `0` 度。
  - 第 2 张在 `-2` 到 `-8` 度间随机。
  - 第 3 张在 `2` 到 `8` 度间随机。
  - 第 4 张再次为负角度随机。
  - 第 5 张再次为正角度随机。
  - 后续每次补位后，按“当前可见索引”重新套用上述正负交替规则。

- 位置分布策略：
  - 基于 `stackLayout` 的可用矩形区域生成候选中心点与偏移量。
  - 对每张卡片计算旋转后的包围 `RectF`。
  - 逐张尝试随机候选位置，只有满足以下条件才接受：
  - 旋转后的包围矩形完全落在 `stackLayout` 内。
  - 与已放置卡片不能“几乎完全重合”。
  - 中心点、交叠面积或交叠比例超过阈值的候选会被丢弃并重试。
  - 如果随机候选多次失败，则退化为一组预设安全偏移，确保最差情况下也能稳定显示 5 张。

### 5. 拖拽、滑出与补位

- 在 `PicStackLayout.kt` 中给最上层卡片添加拖拽行为：
  - 支持手指沿任意方向拖动。
  - 拖动过程中同步更新 `translationX`、`translationY`，并可叠加轻微旋转增强反馈。
  - 松手后如果位移或速度超过阈值，则沿当前方向执行滑出动画。
  - 未达到阈值则回弹到原位。

- 补位规则：
  - 顶层卡片滑出后，从可见列表移除。
  - 剩余卡片依次前移到新的可见索引。
  - 如果前 N 个数据里还有未展示项，则补入下一张到堆叠底层，再统一应用角度与随机位置计算。
  - 如果数据不足 `5` 张，则仅显示实际数量；如果前 N 个数据全部用完，则不再补位。

## Assumptions & Decisions

- 已确认：
  - 外框资源本次先用占位 `shape`。
  - `switchBtn` 与 `rcv` 本次不接逻辑。
  - 仅最上层卡片支持拖拽滑走。
- 本次默认只接图片数据，不把视频纳入堆叠卡片。
- 前 N 的默认值先写在 `PicStackFragment` 或堆叠容器常量中，不做外部配置入口。
- 为满足“边框宽度不确定”这一前提，卡片 View 会保留可配置内边距/边框厚度接口，而不把内层图尺寸写死为固定 dp。
- 由于 `UriParsedInfo` 不带宽高，图片比例以实际加载完成后的 `Drawable` 尺寸为准。

## Verification

- 静态检查
  - 修改完成后检查涉及 Kotlin/XML 文件诊断，确保无新增语法或资源绑定错误。
- 手工验证场景
  - 首次进入页面，授权成功后能拿到媒体并显示最多 `5` 张堆叠卡片。
  - 当媒体数小于 `5`、介于 `5-9`、大于等于 `10` 时，显示数量与补位逻辑正确。
  - 第 1 张保持正向，后续 4 张满足负正交替随机角度规则。
  - 所有卡片即使旋转后也不超出 `stackLayout`。
  - 任意方向拖动最上层卡片，达到阈值后能滑出，未达阈值能回弹。
  - 顶层滑出后第 `6`、`7`... 张按顺序补入，直到前 N 数据耗尽。
