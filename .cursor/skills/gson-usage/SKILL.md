---
name: gson-usage
description: 规定 JsonUtils 顶层函数与全局 Gson 在本项目中的序列化/反序列化方式。在使用 Gson 或 JSON 字符串与对象互转时使用。
---

Gson 扩展工具 (JsonUtils.kt) 顶层函数直接操作：
- 快速转换 ：任意对象可调用 toGsonString() ，Json 字符串可调用 fromGson<T>() 或 fromGsonList<E>() 。
- 类型适配 ：全局 gson 实例默认只注入 JsonUriAdapter 做Uri的适配器。
