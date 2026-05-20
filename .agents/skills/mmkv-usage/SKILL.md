---
name: mmkv-usage
description: 规定 MmkvUtil 顶层函数与 MMKV 属性委托在本项目中的读写与集合用法。在使用 MMKV 做 KV 存储或持久化配置时使用。
---

1. 基础读写 (MmkvUtil.kt) ：通过全顶层函数直接操作，适用于一次性或简单的存取场景。
   - 保存 ： mmkvSetAny 自动识别类型（String, Int, Boolean 等）。若非基础类型，自动通过 Gson 转为 Json 存储。
   - 读取 ： 普通类型，mmkvGetString, mmkvGetInt, mmkvGetLong; 自定义类，mmkvGet<T> 通过泛型自动反序列化。
   - 集合操作 ：提供专门的 mmkvSetArrayList 、 mmkvGetArrayList 、 mmkvSetMap 等函数，给集合操作提供便捷。


2. 属性委托： 通过 by 关键字将变量直接映射到 MMKV，代码最简洁，适用于配置项、用户信息等持久化字段。
- 如果我没有要求，使用忽略使用属性委托。
- 基础类型 ：使用 MMKVStringCache 、 MMKVBooleanCache 、 MMKVIntCache 等。
- 对象/列表 ：使用 MMKVGsonCache ，需传入 typeToken 。
    - 注意 ：修改对象内部属性不会触发自动保存，必须通过“重新赋值”触发 set 操作。
