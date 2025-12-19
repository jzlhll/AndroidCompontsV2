package com.au.module_android.json

import com.au.module_android.Globals
import com.au.module_android.utils.ignoreError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.full.createType

/*
支持情况如下：
总结：
bean类对象要求必须@Serializable注解；
序列化：
    toKsonStringTyped 万能，而且性能好，无需反射，传好对应的serialized()就能正确解析。listToKsonStringTyped/mapToKsonStringTyped只是节约一层泛型传入而已。
    toKsonString 通过反射，无法跨平台，但能用。

    上述2个不支持Any类型，Any是基础类型。
        比如Map<String, Any>，List<Any>, toKsonStringTyped是因为不知道传入什么serializer(); toKsonString() 因为是any无法typeOf。

    toKsonStringLimited 基础类型不需反射，类类型通过反射，因为不是inline，处理的是Any对象，有很多限制，主要是嵌套泛型不能处理。

反序列化：
    fromKson 万能，fromKsonList/fromKsonMap只是节约一层泛型传入而已。
    不支持转成Map<String, Any?>。一般只是单向序列化传给后台，我们不需要转成它。

1.  单个@Serializable注解的_1SerializableBean
        toKsonString()/toKsonStringLimited()/toKsonStringTyped(_1XBean.serialized())/fromJson

2.  List<_1SerializableBean>, Array<_1SerializableBean>, Set<_1SerializableBean>
        toKsonString()/listToKsonStringLimited()/toKsonStringTyped(_1XBean.serialized())/fromKson/fromKsonList

3.  List<_2NormalBean>, Array<_2NormalBean>, Set<_2NormalBean>
        序列化和反序列化都失败
3.1 List<String>
        均支持

4.  Map<String/Int, _1SerializableBean>
        toKsonString()/listToKsonStringLimited()/toKsonStringTyped(String.serialized(), _1XBean.serialized())/fromKson/fromKsonMap

5.  Map<String/Int, _2NormalBean>
        序列化和反序列化都失败

6.  Map<String, Any?>（Any为简单类型）
        toKsonStringLimited()
        toKsonString() 💔不支持 ，toKsonStringTyped 💔不支持，因为不知道怎么传serialized()
        fromJson 💔不支持, 因为泛型不知道传什么，需要序列化注解
6.1  List<Any>
        toKsonStringLimited()
        toKsonString() 💔不支持 ，toKsonStringTyped 💔不支持，因为不知道怎么传serialized()
        fromJson 💔不支持, 因为泛型不知道传什么，需要序列化注解

7.  BaseResultBean<T>（T为_1SerializableBean或_2NormalBean）
        toKsonString()/toKsonStringTyped(BaseResultBean.serialized(_1XBean.serialized()))/fromJson<BaseResultBean<_1XBean>>()
        toKsonStringLimited()💔不支持，提示缺乏泛型；因为是Any的做法，无法知道类型
        普通类型T，都是失败

8.  BaseResultBean<List<_1SerializableBean>>
        toKsonString()/toKsonStringTyped(BaseResultBean.serialized(ListSerializer(_1XBean.serialized())))/fromJson<BaseResultBean<List<_1XBean>>>()
        toKsonStringLimited()💔不支持，提示缺乏泛型；因为是Any的做法，无法知道类型

9.  BaseResultBean<Map<String, _1SerializableBean>>                                   暂时忽略

10. Map<String, List<_1SerializableBean>>
        toKsonString()/listToKsonStringLimited()/toKsonStringTyped(String.serialized(), _1XBean.serialized())/fromKson/fromKsonMap 都支持


11. List<Map<String, _1SerializableBean>>                                               暂时忽略

12. List<BaseResultBean<_1SerializableBean>>
        toKsonString()/toKsonStringTyped(BaseResultBean.serialized(_1XBean.serialized()))
        toKsonStringLimited()💔不支持，提示缺乏泛型；因为是Any的做法，无法知道类型
        fromJson<List<BaseResultBean<_1XBean>>>() / fromJsonList<BaseResultBean<_1XBean>>()

13. _3SerializableNestBean内部包含一个字段_1SerializableBean
        均支持

14. _3SerializableNestBean内部包含一个字段List<_1SerializableBean>
        均支持

15. _3SerializableNestBean内部包含一个字段Map<String, _1SerializableBean>
        均支持

16. BaseResultBean<Pair<_1SerializableBean, _2NormalBean>>                           暂时忽略
17. 密封类sealed class Base的不同子类（都带@Serializable）                                暂时忽略
18. 接口的不同实现类的解析                                                                      暂时忽略
19. 抽象类的具体子类实例                                                                        暂时忽略
 */
/**
 * 专攻List<Any>, Map<String, Any?>的toString。
 *
 * 不支持嵌套泛型。
 * 使用的是反射机制this:class.createType实现的，
 * 对于map/List有额外item解析。
 *
 * 不支持跨平台。其实不太推荐。
 *
 * json序列化。其实还是要求如果是T类型，T必须也是使用了@ Serializable注解才行
 */
@Deprecated("极度受限，使用上位版本[toKsonString]")
fun Any.toKsonStringLimited() : String = Globals.kson.encodeToString(this.toKsonElementLimited())

@Deprecated("极度受限，使用上位版本[toKsonString]")
internal fun Any?.toKsonElementLimited(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Array<*> -> JsonArray(map { it.toKsonElementLimited() })
    is List<*> -> JsonArray(map { it.toKsonElementLimited() })
    is Map<*, *> -> JsonObject(map { it.key.toString() to it.value.toKsonElementLimited() }.toMap())

    //经过测试，其实这里也是要求this这个class，必须是使用@ Serializable注解的。因此尽量使用toJsonStringTyped
    //同时对于嵌套泛型的解析，这是无法支持的。因为这里仅是拿了第一层。
    else -> Globals.kson.encodeToJsonElement(serializer(this::class.createType()), this)
}

/**
 * inline+KSerializer让编译时就确定类型准确无误。
 * 嵌套类型比如：loginBean.toJsonStringTyped(ResultBean.serializer(LoginResponse.serializer()))
 */
inline fun <reified T> T.toKsonString() : String = Globals.kson.encodeToString(this)

inline fun <reified T> String.fromKson() = ignoreError { Globals.kson.decodeFromString<T>(this) }