解析图片实现 API 接口规则：

# Response 类风格
- 如果图片中的response 200 的成功结果是{code, msg, status}则使用requestApi的方式调用,并且不用添加 Response 类
- class使用 public final 定义；字段使用 public
- 使用驼峰命名，并使用SerializedName注解来匹配接口字段，如果不是下划线，不需要注解

# API 实现规则
- 参考 Api 中其他函数的实现
- 阅读AbsOkhttpApi
- 函数命名以图片中的中文描述总结后取名，不以图片的名字为准
- 如果有嵌套的List对象，需要使用Nullable注解