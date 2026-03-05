---
name: gson使用规则
description: 当你想使用Gson时，需了解它。
---

Gson 扩展工具 (JsonUtils.kt) 顶层函数直接操作：
- 快速转换 ：任意对象可调用 toGsonString() ，Json 字符串可调用 fromGson<T>() 或 fromGsonList<E>() 。
- 类型适配 ：全局 gson 实例默认只注入 JsonUriAdapter 做Uri的适配器。