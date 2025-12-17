package com.allan.androidlearning.kson

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.viewbinding.ViewBinding
import com.allan.androidlearning.databinding.HolderKsonIteBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.json.fromJson
import com.au.module_android.json.lisToKsonStringTyped
import com.au.module_android.json.toKsonString
import com.au.module_android.json.toKsonStringTyped
import com.au.module_android.selectlist.SimpleItem
import com.au.module_android.selectlist.SimpleListFragment
import com.au.module_android.utils.HtmlPart
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.useSimpleHtmlText
import com.au.module_androidui.databinding.KsonTemprorayViewBinding
import com.au.module_androidui.dialogs.AbsCenterFakeDialog
import kotlinx.serialization.builtins.serializer
import java.util.Date
import kotlin.toString

class KsonItem(override val itemName: String,
               override val onItemClick: () -> Unit) : SimpleItem() {
}

@EntryFrgName(priority = 100)
class KsonTestFragment(
    override val title: String = "KsonTest",
) : SimpleListFragment<KsonItem>() {

    private val temporaryView = TemporaryView()
    private val testFunc = JsonUtilTestFunctions()

    private fun create1_simple_toString(): KsonItem {
        return KsonItem("1. 简单对象序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringSimple()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create1_simple_toStringTyped(): KsonItem {
        return KsonItem("1. 简单对象序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringSimpleTyped()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create1_simple_toStringLimited(): KsonItem {
        return KsonItem("1. 简单对象序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringSimpleLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create1_simple_fromString(): KsonItem {
        return KsonItem("1. 简单对象从字符串反序列化") {
            val r = testFunc.testFromStringSimple()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create2_annotatedList_toStringDefault(): KsonItem {
        return KsonItem("2. 注解类列表序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringListAnnotatedDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create2_annotatedList_toString(): KsonItem {
        return KsonItem("2. 注解类列表序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringListAnnotated()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create2_annotatedList_toStringLimited(): KsonItem {
        return KsonItem("2. 注解类列表序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringListAnnotatedLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create2_annotatedList_fromString(): KsonItem {
        return KsonItem("2. 注解类列表从字符串反序列化") {
            val r = testFunc.testFromStringListAnnotated()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create2_annotatedList_fromString2(): KsonItem {
        return KsonItem("2. 注解类列表从字符串反序列化（List专用）") {
            val r = testFunc.testFromStringListAnnotated2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create3_normalList_toString(): KsonItem {
        return KsonItem("3. 普通类列表序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringListNormal()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalList_toStringDefault(): KsonItem {
        return KsonItem("3. 普通类列表序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringListNormalDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalList_toStringLimited(): KsonItem {
        return KsonItem("3. 普通类列表序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringListNormalLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalList_fromString(): KsonItem {
        return KsonItem("3. 普通类列表从字符串反序列化") {
            val r = testFunc.testFromStringListNormal()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create3_normalList_fromString2(): KsonItem {
        return KsonItem("3. 普通类列表从字符串反序列化（List专用）") {
            val r = testFunc.testFromStringListNormal2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    /////
    private fun create3_normalListStr_toString(): KsonItem {
        return KsonItem("3. 普通类列表string序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringListStrNormal()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalListStr_toStringDefault(): KsonItem {
        return KsonItem("3. 普通类列表string序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringListStrNormalDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalListStr_toStringLimited(): KsonItem {
        return KsonItem("3. 普通类列表string序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringListStrNormalLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create3_normalListStr_fromString(): KsonItem {
        return KsonItem("3. 普通类列表string从字符串反序列化") {
            val r = testFunc.testFromStringListStrNormal()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }
    ////

    private fun create4_annotatedMap_toString(): KsonItem {
        return KsonItem("4. 注解类Map序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringMapAnnotated()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create4_annotatedMap_toStringDefault(): KsonItem {
        return KsonItem("4. 注解类Map序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringMapAnnotatedDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create4_annotatedMap_toStringIntDefault(): KsonItem {
        return KsonItem("4. 注解类Map序列化 (Default) Int") {
            val (obj, serializedStr) = testFunc.testToStringMapAnnotatedIntDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }


    private fun create4_annotatedMap_toStringLimited(): KsonItem {
        return KsonItem("4. 注解类Map序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringMapAnnotatedLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create4_annotatedMap_fromString(): KsonItem {
        return KsonItem("4. 注解类Map从字符串反序列化") {
            val r = testFunc.testFromStringMapAnnotated()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create4_annotatedMap_fromString2(): KsonItem {
        return KsonItem("4. 注解类Map从字符串反序列化（Map专用）") {
            val r = testFunc.testFromStringMapAnnotated2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create5_normalMap_toString(): KsonItem {
        return KsonItem("5. 普通类Map序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringMapNormal()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create5_normalMap_toStringDefault(): KsonItem {
        return KsonItem("5. 普通类Map序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringMapNormalDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create5_normalMap_toStringLimited(): KsonItem {
        return KsonItem("5. 普通类Map序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringMapNormalLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create5_normalMap_fromString(): KsonItem {
        return KsonItem("5. 普通类Map从字符串反序列化") {
            val r = testFunc.testFromStringMapNormal()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create5_normalMap_fromString2(): KsonItem {
        return KsonItem("5. 普通类Map从字符串反序列化（Map专用）") {
            val r = testFunc.testFromStringMapNormal2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create6_simpleMap_toString(): KsonItem {
        return KsonItem("6. 简单类型Map序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringMapSimple()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create6_simpleMap_toStringTyped(): KsonItem {
        return KsonItem("6. 简单类型Map序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringMapSimpleTyped()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create6_simpleMap_toStringLimited(): KsonItem {
        return KsonItem("6. 简单类型Map序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringMapSimpleLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create6_simpleMap_fromString(): KsonItem {
        return KsonItem("6. 简单类型Map从字符串反序列化") {
            val r = testFunc.testFromStringMapSimple()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create6_simpleMap_fromString2(): KsonItem {
        return KsonItem("6. 简单类型Map从字符串反序列化（Map专用）") {
            val r = testFunc.testFromStringMapSimple2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create7_genericAnnotated_toString(): KsonItem {
        return KsonItem("7. 泛型结果-注解类型序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomAnnotated()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericAnnotated_toStringDefault(): KsonItem {
        return KsonItem("7. 泛型结果-注解类型序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomAnnotatedDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericAnnotated_toStringLimited(): KsonItem {
        return KsonItem("7. 泛型结果-注解类型序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomAnnotatedLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericAnnotated_fromString(): KsonItem {
        return KsonItem("7. 泛型结果-注解类型从字符串反序列化") {
            val r = testFunc.testFromStringGenericCustomAnnotated()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create7_genericNormal_toString(): KsonItem {
        return KsonItem("7. 泛型结果-普通类型序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomNormal()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericNormal_toStringDefault(): KsonItem {
        return KsonItem("7. 泛型结果-普通类型序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomNormalDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericNormal_toStringLimited(): KsonItem {
        return KsonItem("7. 泛型结果-普通类型序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomNormalLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create7_genericNormal_fromString(): KsonItem {
        return KsonItem("7. 泛型结果-普通类型从字符串反序列化") {
            val r = testFunc.testFromStringGenericCustomNormal()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create8_nestedList_toString(): KsonItem {
        return KsonItem("8. 嵌套泛型-列表序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringNestedGenericList()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create8_nestedList_toStringDefault(): KsonItem {
        return KsonItem("8. 嵌套泛型-列表序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringNestedGenericListDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create8_nestedList_toStringLimited(): KsonItem {
        return KsonItem("8. 嵌套泛型-列表序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringNestedGenericListLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create8_nestedList_fromString(): KsonItem {
        return KsonItem("8. 嵌套泛型-列表从字符串反序列化") {
            val r = testFunc.testFromStringNestedGenericList()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create10_nestedMapList_toString(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringNestedMapList()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create10_nestedMapList_toStringDefault(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringNestedMapListDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create10_nestedMapList_toStringLimited(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringNestedMapListLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create10_nestedMapList_fromString(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>从字符串反序列化") {
            val r = testFunc.testFromStringNestedMapList()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create10_nestedMapList_fromString2(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>从字符串反序列化（Map专用）") {
            val r = testFunc.testFromStringNestedMapList2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create12_nestedListGeneric_toString(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringNestedListGeneric()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create12_nestedListGeneric_toStringDefault(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringNestedListGenericDefault()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create12_nestedListGeneric_toStringLimited(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringNestedListGenericLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create12_nestedListGeneric_fromString(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果从字符串反序列化") {
            val r = testFunc.testFromStringNestedListGeneric()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create12_nestedListGeneric_fromString2(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果从字符串反序列化（List专用）") {
            val r = testFunc.testFromStringNestedListGeneric2()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create13_deepNestField_toString(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestField()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create13_deepNestField_toStringTyped(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestFieldTyped()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create13_deepNestField_toStringLimited(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestFieldLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create13_deepNestField_fromString(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段从字符串反序列化") {
            val r = testFunc.testFromStringDeepNestField()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create14_deepNestList_toString(): KsonItem {
        return KsonItem("14. 深度嵌套-列表字段序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestList()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create14_deepNestList_toStringTyped(): KsonItem {
        return KsonItem("14. 深度嵌套-列表字段序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestListTyped()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create14_deepNestList_toStringLimited(): KsonItem {
        return KsonItem("14. 深度嵌套-列表字段序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestListLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create14_deepNestList_fromString(): KsonItem {
        return KsonItem("14. 深度嵌套-列表字段从字符串反序列化") {
            val r = testFunc.testFromStringDeepNestList()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create15_deepNestMap_toString(): KsonItem {
        return KsonItem("15. 深度嵌套-Map字段序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestMap()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create15_deepNestMap_toStringTyped(): KsonItem {
        return KsonItem("15. 深度嵌套-Map字段序列化 (Typed)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestMapTyped()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create15_deepNestMap_toStringLimited(): KsonItem {
        return KsonItem("15. 深度嵌套-Map字段序列化 (Limited)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestMapLimited()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create15_deepNestMap_fromString(): KsonItem {
        return KsonItem("15. 深度嵌套-Map字段从字符串反序列化") {
            val r = testFunc.testFromStringDeepNestMap()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private val _items = listOf(
        create1_simple_toString(),
        create1_simple_toStringTyped(),
        create1_simple_toStringLimited(),
        create1_simple_fromString(),
        create2_annotatedList_toStringDefault(),
        create2_annotatedList_toString(),
        create2_annotatedList_toStringLimited(),
        create2_annotatedList_fromString(),
        create2_annotatedList_fromString2(),
        create3_normalList_toString(),
        create3_normalList_toStringDefault(),
        create3_normalList_toStringLimited(),
        create3_normalList_fromString(),
        create3_normalList_fromString2(),

        create3_normalListStr_toString(),
        create3_normalListStr_toStringDefault(),
        create3_normalListStr_toStringLimited(),
        create3_normalListStr_fromString(),

        create4_annotatedMap_toString(),
        create4_annotatedMap_toStringDefault(),
        create4_annotatedMap_toStringIntDefault(),
        create4_annotatedMap_toStringLimited(),
        create4_annotatedMap_fromString(),
        create4_annotatedMap_fromString2(),
        create5_normalMap_toString(),
        create5_normalMap_toStringDefault(),
        create5_normalMap_toStringLimited(),
        create5_normalMap_fromString(),
        create5_normalMap_fromString2(),
        create6_simpleMap_toString(),
        create6_simpleMap_toStringTyped(),
        create6_simpleMap_toStringLimited(),
        create6_simpleMap_fromString(),
        create6_simpleMap_fromString2(),
        create7_genericAnnotated_toString(),
        create7_genericAnnotated_toStringDefault(),
        create7_genericAnnotated_toStringLimited(),
        create7_genericAnnotated_fromString(),
        create7_genericNormal_toString(),
        create7_genericNormal_toStringDefault(),
        create7_genericNormal_toStringLimited(),
        create7_genericNormal_fromString(),
        create8_nestedList_toString(),
        create8_nestedList_toStringDefault(),
        create8_nestedList_toStringLimited(),
        create8_nestedList_fromString(),
        create10_nestedMapList_toString(),
        create10_nestedMapList_toStringDefault(),
        create10_nestedMapList_toStringLimited(),
        create10_nestedMapList_fromString(),
        create10_nestedMapList_fromString2(),
        create12_nestedListGeneric_toString(),
        create12_nestedListGeneric_toStringDefault(),
        create12_nestedListGeneric_toStringLimited(),
        create12_nestedListGeneric_fromString(),
        create12_nestedListGeneric_fromString2(),
        create13_deepNestField_toString(),
        create13_deepNestField_toStringTyped(),
        create13_deepNestField_toStringLimited(),
        create13_deepNestField_fromString(),
        create14_deepNestList_toString(),
        create14_deepNestList_toStringTyped(),
        create14_deepNestList_toStringLimited(),
        create14_deepNestList_fromString(),
        create15_deepNestMap_toString(),
        create15_deepNestMap_toStringTyped(),
        create15_deepNestMap_toStringLimited(),
        create15_deepNestMap_fromString(),
    )


    override val items: List<KsonItem>
        get() = _items

    override fun createItemView(context: Context, value: KsonItem): ViewBinding {
        return HolderKsonIteBinding.inflate(LayoutInflater.from(context))
    }

    override fun bindItemView(vb: ViewBinding, item: KsonItem) {
        vb as HolderKsonIteBinding
        vb.text.text = item.itemName
        vb.root.onClick {
            item.onItemClick()
        }
    }

    private fun ksonTest() {
        val cookie = okhttp3.Cookie.Builder()
            .name("xiao ming")
            .value("api_token")
            .domain("api.com").build()

        try {
            val cookieStr = cookie.toKsonString()
            logdNoFile("🌟kson") { "third class $cookieStr" }
            logdNoFile("🌟kson") { "-------第三方直接toKsonString success-------\n$cookieStr\n" }
        } catch (e: Exception) {
            e.printStackTrace()
            logdNoFile("🌟kson") { "-------第三方直接toKsonString error-------" }
        }

        try {
            val cookieStr = cookie.toKsonStringTyped(CookieSerializer)
            logdNoFile("🌟kson") { "-------第三方toKsonStringTyped(CookieSerializer) to success-------\n$cookieStr\n" }
        } catch (e: Exception) {
            e.printStackTrace()
            logdNoFile("🌟kson") { "-------第三方toKsonStringTyped(CookieSerializer) error-------" }
        }

        val cookieBean = CookieStoreBean()
        cookieBean.cookies = listOf(cookie)
        try {
            val cookieStr1 = cookieBean.toKsonString()
            logdNoFile("🌟kson") { "cookieBean Str1 $cookieStr1" }
            val cookieStr2 = cookieBean.toKsonStringTyped(CookieStoreBean.serializer())
            logdNoFile("🌟kson") { "cookieBean Str2 $cookieStr2" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logdNoFile("🌟kson") { "-------3-------" }

        val cookieStrings = """
            {"cookies":[{"name":"xiao li","value":"api_token","expiresAt":253402300799999,"domain":"domain.com","path":"/","secure":false,"httpOnly":true,"persistent":false,"hostOnly":false}]}
        """.trimIndent()
        try {
            val b = cookieStrings.fromJson<CookieStoreBean>()
            logdNoFile("🌟kson") { "cookie Bean $b" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logdNoFile("🌟kson") { "-------4-------" }

        val date = Date(System.currentTimeMillis())
        val color = Color.valueOf(1f, 0f, 0f)
        val customBean = CustomBean(color, Uri.parse("https://www.baidu.com"), date)
        try {
            val customBeanStr = customBean.toKsonString()
            logdNoFile("🌟kson") { "customBean Str $customBeanStr" }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val yesterday = 1765874092976L - 24 * 60 * 60 * 1000
        val customBeanStr = """
            {"color":{"argb":-65536},"uuid":{"uri":"https://www.google.com"},"date":{"epochMillis":$yesterday}}
        """.trimIndent()
        try {
            val customBean = customBeanStr.fromJson<CustomBean>()
            logdNoFile("🌟kson") { "customBean $customBean" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logdNoFile("🌟kson") { "-------5-------" }
    }

    private fun ksonTestCollection() {
        try {
            val list = listOf("aaaa", "bb", "ccc")
            val listStr1 = list.toKsonString()
            logdNoFile("🌟kson") { "listStr $listStr1" }

            val listStr2 = list.lisToKsonStringTyped(String.serializer())
            logdNoFile("🌟kson") { "listStr2 $listStr2" }

            val origStr = """
                ["eee","yyyy","xxx"]
            """.trimIndent()
            val origList = origStr.fromJson<List<String>>()
            logdNoFile("🌟kson") { "origList $origList" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logdNoFile("🌟kson") { "-------1-------" }

        try {
            val loginBean = BaseResultBean("100", "message11", true, Tokens("token_111", "refresh_token_111"))
            val loginBeanStr =  loginBean.toKsonString()
            logdNoFile("🌟kson") { "loginBeanStr1 $loginBeanStr" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val loginBean = BaseResultBean("100", "message11", true, Tokens("token_111", "refresh_token_111"))
            val logBeanStr = loginBean.toKsonStringTyped(BaseResultBean.serializer(Tokens.serializer()))
            logdNoFile("🌟kson") { "loginBeanStr3 $logBeanStr" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logdNoFile("🌟kson") { "-------2-------" }

        try {
            val map = mapOf("a" to "aaa", "b" to "bbb", "c" to "ccc")
            val mapStr1 = map.toKsonString()
            logdNoFile("🌟kson") { "mapStr $mapStr1" }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        logdNoFile("🌟kson") { "-------3-------" }

    }
}

class TemporaryView : AbsCenterFakeDialog<KsonTemprorayViewBinding>() {
    private lateinit var mText:Array<HtmlPart>

    fun serialize(fragment: KsonTestFragment, origObject:String?, serializedString:String?) {
        val htmlParts = arrayOf(HtmlPart("序列化\n\n"),
            HtmlPart((origObject ?: "") + "\n\n", "#0055ff"),
            HtmlPart((serializedString ?: ""), "#666666"))
        mText = htmlParts
        pop(fragment)
    }

    fun deserialize(fragment: KsonTestFragment, origSerializedString:String?, toObject:String?) {
        val htmlParts = arrayOf(HtmlPart("反序列化\n\n"),
            HtmlPart((origSerializedString ?: "") + "\n\n", "#666666"),
            HtmlPart((toObject ?: ""), "#0055ff"))
        mText = htmlParts
        pop(fragment)
    }

    override fun onShow(activity: ComponentActivity, binding: KsonTemprorayViewBinding) {
        binding.root.onClick {
            hide()
        }
        binding.font.useSimpleHtmlText(*mText)
    }

    override fun onHide(binding: KsonTemprorayViewBinding) {
    }

}
