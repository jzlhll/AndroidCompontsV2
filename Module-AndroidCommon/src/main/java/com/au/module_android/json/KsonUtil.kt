package com.au.module_android.json

import com.au.module_android.Globals
import com.au.module_android.utils.ignoreError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * 使用的是反射机制实现的，不支持跨平台。
 * 而且也不能支持非@Serializable注解的class。
 * 能支持嵌套泛型，因为它使用inline，T在编译的时候，成了真实类型。
 *
 *  简单的List String, Map<String, Any?>，其中Any是简单类型。
 *  简单的List<MyClass> 其中MyClass已经使用了@ Serializable注解
 *  简单的Map<String, MyClass> 其中MyClass已经使用了@ Serializable注解
 *
 */
@Deprecated("")
inline fun <reified T> T.toKsonString() = Globals.kson.encodeToString(Json.serializersModule.serializer(typeOf<T>()), this)

/**
 * inline+KSerializer让编译时就确定类型准确无误。[toJsonString]的标准版。
 * 嵌套类型比如：loginBean.toJsonStringTyped(ResultBean.serializer(LoginResponse.serializer()))
 */
inline fun <reified T> T.toKsonStringTyped(serializer: KSerializer<T>) : String = Globals.kson.encodeToString(serializer, this)

inline fun <reified E> List<E>.toKsonStringTyped(serializer: KSerializer<E>) : String
    = Globals.kson.encodeToString(ListSerializer(serializer), this)

inline fun <reified K, reified V> Map<K, V>.toKsonStringTyped(kSerializer: KSerializer<K>, vSerializer: KSerializer<V>) : String
    = Globals.kson.encodeToString(MapSerializer(kSerializer, vSerializer), this)

/**
 *
 * json序列化。其实还是要求如果是T类型，T必须也是使用了@ Serializable注解才行
 * 极度受限，使用上位版本[toJsonString]
 *
 *  什么时候，可以使用它呢？
 *
 *  简单的List String, Map<String, Any?>，其中Any是简单类型。
 *  简单的List<MyClass> 其中MyClass已经使用了@ Serializable注解
 *  简单的Map<String, MyClass> 其中MyClass已经使用了@ Serializable注解
 */
@Deprecated("极度受限，使用上位版本[toJsonString]？")
fun Any.toKsonStringLimited() : String = Globals.kson.encodeToString(this.toKsonElementLimited())

@Deprecated("极度受限，使用上位版本[toJsonString]？")
fun Any?.toKsonElementLimited(): JsonElement = when (this) {
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

inline fun <reified T> String.fromKson() = ignoreError { Globals.kson.decodeFromString<T>(this) }

inline fun <reified E> String.fromKsonList(): List<E> =
    ignoreError { Globals.kson.decodeFromString<List<E>>(this) } ?: emptyList()


