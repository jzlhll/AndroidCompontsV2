---
name: Api自动生成
description: 当让你生成新的Api(文字或解析图片)实现函数/接口遵守它。
---

# Response类
响应格式为{code, message/msg, status}时，用requestApi调用，无需创建Response类
类定义：public final
字段：public
驼峰命名，仅当下划线命名时使用SerializedName注解

# API实现
参考现有Api函数
阅读AbsOkhttpApi
函数命名：基于图片中文描述总结，非图片文件名
嵌套List对象需加Nullable注解