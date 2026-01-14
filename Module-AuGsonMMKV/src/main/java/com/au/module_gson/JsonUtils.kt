package com.au.module_gson

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Type

val gson = Gson()

/**
 * 扩展：将任意对象，转成jsonString。
 * Bundle 可能会直接出错。
 */
fun Any.toGsonString() : String {
    if (isBaseType()) {
        return "" + this
    }
    return gson.toJson(this)
}

/**
 * 检查是不是基础类型
 */
private fun Any.isBaseType() : Boolean{
    when (this) {
        is Byte, Boolean, Short, Char, Float, Double, Int, Long, String -> return true
    }
    return false
}

/**
 * 扩展：将string转成任意类型的对象
 * org.jetbrains.kotlin.android <=1.8.10 有bug不支持二级嵌套xxxBean<CmdData>
 */
@Throws(JsonSyntaxException::class)
inline fun <reified T> String.fromGson() : T? {
    if (T::class.java == String::class.java) {
        return this as T
    }
    return gson.fromJson(this, object : TypeToken<T>() {}.type)
}

/**
 * 扩展：将string转成任意类型List的对象。
 *
 * @param typeToken 如下：
 * 普通类：MyClass::class.java 或 (TypeToken.get(MyClass::class.java))
 *
 * List<MyClass> : TypeToken.getParameterized(List::class.java, MyClass::class.java).type
 *
 * 自定义嵌套泛型MyClass<TypeArg>: TypeToken.getParameterized(MyClass::class.java, TypeArg::class.java).type
 *
 * Map<String, MyClass> ：TypeToken.getParameterized(Map::class.java, String::class.java, MyClass::class.java).type
 *
 * 更复杂的List<Map<String, MyClass>>：
 * val mapType = TypeToken.getParameterized(Map::class.java, String::class.java, MyClass::class.java).type
 * val listType = TypeToken.getParameterized(List::class.java, mapType).type
 */
fun <T> fromGson(jsonStr: String?, typeToken: Type): T {
    return GsonBuilder().create().fromJson<T>(jsonStr, typeToken)
}

//JSONArray扩展函数
fun JSONArray.foreachJSONObject(block:(JSONObject)->Unit) {
    val len = this.length()
    for (i in 0 until len) {
        val one = this.getJSONObject(i)
        block(one)
    }
}

/**
 * 扩展：将string转成任意类型List的对象
 */
inline fun <reified E> String.fromGsonList() : List<E> {
    return fromGsonList(E::class.java)
}

/**
 * 扩展：将string转成任意类型List的对象
 */
fun <E> String.fromGsonList(elementClass:Class<E>) : List<E> {
    //return gson.fromGson(strJson, TypeToken<List<T>>() {}.getType());
    //改为下面的方法，clazz传入实际想要解析出来的类
    //return BaseGlobalConst.gson.fromGson(json, object : TypeToken<List<T>>() {}.type)
    try {
        val listType : Type = TypeToken.getParameterized(ArrayList::class.java, elementClass).type
        return gson.fromJson(this, listType)
    } catch (e:JsonSyntaxException) {
        e.printStackTrace()
    }
    return emptyList()
}

private fun formatGsonBeautifulJsonStr(str:String) : String {
    return try {
        val jsonElement: JsonElement = JsonParser.parseString(str)
        val beautifulGson = GsonBuilder().setPrettyPrinting().create()
        return beautifulGson.toJson(jsonElement)
    } catch (e:Exception) {
        str
    }
}

fun formatGsonBeautiful(obj:Any) : String {
    if (obj is String) {
        return formatGsonBeautifulJsonStr(obj)
    }
    return try {
        val beautifulGson = GsonBuilder().setPrettyPrinting().create()
        return beautifulGson.toJson(obj)
    } catch (e:Exception) {
        ""
    }
}