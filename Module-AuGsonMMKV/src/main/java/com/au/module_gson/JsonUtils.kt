package com.au.module_gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Type

val gson = Gson()

/**
 * 扩展：将任意对象，转成jsonString。
 * Bundle 可能会直接出错。
 */
fun Any.toJsonString() : String {
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
inline fun <reified T> String.fromJson() : T? {
    if (T::class.java == String::class.java) {
        return this as T
    }
    return gson.fromJson(this, object : TypeToken<T>() {}.type)
}

/**
 * java版本：将string转成任意类型的对象
 */
fun <T> fromJson(jsonStr: String?, t: Class<T>): T {
    val typeToken = TypeToken.getParameterized(t).type
    return GsonBuilder().create().fromJson<T>(jsonStr, typeToken)
}

//二级泛型解析：使用TypeToken的版本。org.jetbrains.kotlin.android >1.8.10 则无需如此。
//inline fun <reified T, reified TLv2> String.fromJsonLv2(): T {
//    if (T::class.java == String::class.java) {
//        return this as T
//    }
//    val typeToken = TypeToken.getParameterized(T::class.java, TLv2::class.java).type
//    return gson.fromJson(this, typeToken)
//}

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
inline fun <reified E> String.fromJsonList() : List<E> {
    return fromJsonList(E::class.java)
}

/**
 * 扩展：将string转成任意类型List的对象
 */
fun <E> String.fromJsonList(elementClass:Class<E>) : List<E> {
    //return gson.fromJson(strJson, TypeToken<List<T>>() {}.getType());
    //改为下面的方法，clazz传入实际想要解析出来的类
    //return BaseGlobalConst.gson.fromJson(json, object : TypeToken<List<T>>() {}.type)
    try {
        val listType : Type = TypeToken.getParameterized(ArrayList::class.java, elementClass).type
        return gson.fromJson(this, listType)
    } catch (e:JsonSyntaxException) {
        e.printStackTrace()
    }
    return emptyList()
}

private fun formatJsonBeautifulJsonStr(str:String) : String {
    return try {
        val jsonElement: JsonElement = JsonParser.parseString(str)
        val beautifulGson = GsonBuilder().setPrettyPrinting().create()
        return beautifulGson.toJson(jsonElement)
    } catch (e:Exception) {
        str
    }
}

fun formatJsonBeautiful(obj:Any) : String {
    if (obj is String) {
        return formatJsonBeautifulJsonStr(obj)
    }
    return try {
        val beautifulGson = GsonBuilder().setPrettyPrinting().create()
        return beautifulGson.toJson(obj)
    } catch (e:Exception) {
        ""
    }
}