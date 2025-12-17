需要涵盖如下测试项：
写测试用例，其中你可以使用的函数为KsonUtil中的几个函数。下面详细讲述要求：
- 暂时忽略的，不写测试用例
- 你不用管kotlinx.serialization的任何语法，比如涉及到@Serializable注解的类你可以使用serializer()函数或者String，Int等普通类也可以使用serializer()函数即可，不允许添加任何的KSerializer转换代码
- 如果测试用例无法使用kson目录中的_1xxx, _2xxx, _3xxx等类，请立刻停止工作，询问我需要添加什么样子的Bean类，由我添加后，重新跟你对话

主要分为两大类：
# 第一大类 toString 又分为三小类
注意是必须包含如下三小类：
- toKsonString
- toKsonStringTyped, 需要传入类的serializer()函数, 如果是List使用lisToKsonStringTyped 传入item的serializer()函数；如果是Map使用mapToKsonStringTyped，传入Key和Value的serializer()函数，比如K是String就是String.serializer()
- toKsonStringLimited

# 第二大类 fromString 分为一类
一般情况编写1个函数的测试：fromKson；
并且，如果数据或者类的结构是List追加fromKsonList，Map追加fromKsonMap，注意是追加，即需要写2个测试。

# 测试用例代码示例
toString测试模板
```kotlin
    fun testToStringXXX() : String?{
        try {
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString $str" }
            return str
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
```
fromString测试模板：所有fromString的测试用例，都使用Pair<Boolean, Any?> 返回，不需要将Any变成具体的类型
```kotlin
fun testfromStringXXX() : Pair<Boolean, Any?>{
    val json = """
                    {"avatar":"bbb.com","created_at":1765902046752, "email":"j@gmail.com"}
                """.trimIndent()
    val bean = ignoreError { json.fromKson<_2NormalBean>() }
    logdNoFile("🌟kson") { "fromKson $bean" }
    return (bean != null) to bean
}
```

# 测试覆盖内容

1. 简单对象                     单个@Serializable注解的_1SerializableBean

2. 列表-注解类                  List<_1SerializableBean>, Array<_1SerializableBean>, Set<_1SerializableBean>
3. 列表-普通类                  List<_2NormalBean>, Array<_2NormalBean>, Set<_2NormalBean>

4. Map-注解类                  Map<String/Int, _1SerializableBean>
5. Map-普通类                  Map<String/Int, _2NormalBean>
6. Map-简单类型                Map<String, Any?>（Any为简单类型）

7. 自定义泛型类                 BaseResultBean<T>（T为_1SerializableBean或_2NormalBean）

8. 嵌套泛型结构                 BaseResultBean<List<_1SerializableBean>>
9. 嵌套泛型结构     暂时忽略    BaseResultBean<Map<String, _1SerializableBean>>                             

10. 嵌套泛型结构                Map<String, List<_1SerializableBean>>
11. 嵌套泛型结构    暂时忽略    List<Map<String, _1SerializableBean>>
12. 嵌套泛型结构                List<BaseResultBean<_1SerializableBean>>

13. 深度嵌套结构                _3SerializableNestBean内部包含一个字段 _1SerializableBean
14. 深度嵌套结构                _3SerializableNestBean内部包含一个字段 List<_1SerializableBean>
15. 深度嵌套结构                _3SerializableNestBean内部包含一个字段 Map<String, _1SerializableBean>

16. 复合类型参数    暂时忽略    BaseResultBean<Pair<_1SerializableBean, _2NormalBean>>         备注：这只是转换器的问题

17. 多态/继承场景   暂时忽略    密封类sealed class Base的不同子类（都带@Serializable）
18. 多态/继承场景   暂时忽略    接口的不同实现类的解析
19. 多态/继承场景   暂时忽略    抽象类的具体子类实例