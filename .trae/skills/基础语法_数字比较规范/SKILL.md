---
name: 数字比较规范
description: 当涉及 Int、Long、Float、Double 的大小比较、边界限制、限高限宽、非负收敛、最大最小值裁剪时，统一使用 kotlin.math.min 与 kotlin.math.max，不使用 coerce 系列及其他不直观的比较写法。
---

# 数字比较规范

适用场景：

- 高度、宽度、边距、下标、数量等数值比较
- 非负收敛、最大值限制、最小值保护、区间裁剪
- `Int` 与 `Long` 混合计算后的边界比较

## 规则

- 涉及数字大小比较时，统一优先使用 `min(a, b)`、`max(a, b)`
- 不使用 `coerceAtLeast(...)`、`coerceAtMost(...)`、`coerceIn(...)`、`minOf(...)`、`maxOf(...)`、`compareTo(...)`、`compareValues(...)`
- 原因：`min`、`max` 更直观，更容易一眼看懂“取最大”还是“取最小”
- 能用 `min`、`max` 表达清楚时，不再引入其他比较辅助函数

## 常用写法

```kotlin
val safeHeight = max(height, 0)
val finalHeight = min(targetHeight, maxHeight)
val finalValue = min(max(value, minValue), maxValue)
```

## 混合类型

如果存在 `Int` 与 `Long` 混合计算，先显式转型，再做 `min`、`max` 比较：

```kotlin
val targetHeight = height.toLong() + navigationBarHeight.toLong()
val finalHeight = min(targetHeight, maxHeight.toLong()).toInt()
```
