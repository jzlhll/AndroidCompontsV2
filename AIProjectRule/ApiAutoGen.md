解析图片实现 API 接口规则：
# Response类
响应格式为{code, msg, status}时，用requestApi调用，无需创建Response类
类定义：public final
字段：public
驼峰命名，仅当下划线命名时使用SerializedName注解

# API实现
参考现有Api函数
阅读AbsOkhttpApi
函数命名：基于图片中文描述总结，非图片文件名
嵌套List对象需加Nullable注解