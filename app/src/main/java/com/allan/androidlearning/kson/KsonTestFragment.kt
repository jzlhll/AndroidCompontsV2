package com.allan.androidlearning.kson

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.viewbinding.ViewBinding
import com.allan.androidlearning.databinding.HolderKsonIteBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.Globals.kson
import com.au.module_android.click.onClick
import com.au.module_android.json.fromJson
import com.au.module_android.json.fromKson
import com.au.module_android.json.toKsonString
import com.au.module_android.selectlist.SimpleItem
import com.au.module_android.selectlist.SimpleListFragment
import com.au.module_android.utils.HtmlPart
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.useSimpleHtmlText
import com.au.module_androidui.databinding.KsonTemprorayViewBinding
import com.au.module_androidui.dialogs.AbsCenterFakeDialog
import com.au.module_androidui.toast.ToastUtil.toastOnTop
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.Date

class KsonItem(override val itemName: String,
               override val onItemClick: () -> Unit) : SimpleItem() {
}

@EntryFrgName(priority = 100)
class KsonTestFragment(
    override val title: String = "KsonTest",
) : SimpleListFragment<KsonItem>() {

    private val temporarySimpleView = TemporarySimpleView()
    private val temporaryView = TemporaryView()
    private val testFunc = JsonUtilTestFunctions()

    private fun create1_simple_toString(): KsonItem {
        return KsonItem("1. 简单对象序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringSimple()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create1_simple_toStringTypedNoParam(): KsonItem {
        return KsonItem("1. 简单对象序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringSimpleTypedNoParam()
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

    private fun create2_annotatedList_toStringTypedNoParam(): KsonItem {
        return KsonItem("2. 注解类列表序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringListAnnotatedTypedNoParam()
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
        return KsonItem("2. 注解类列表从字符串反序列化 ") {
            val r = testFunc.testFromStringListAnnotated()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create3_normalList_toStringTypedNoParam(): KsonItem {
        return KsonItem("3. 普通类列表序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringListNormalTypedNoParam()
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
        return KsonItem("3. 普通类列表从字符串反序列化 ") {
            val r = testFunc.testFromStringListNormal()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    /////
    private fun create3_normalListStr_toStringTypedNoParam(): KsonItem {
        return KsonItem("3. 普通类列表string序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringListStrNormalTypedNoParam()
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
    private fun create4_annotatedMap_toStringTypedNoParam(): KsonItem {
        return KsonItem("4. 注解类Map序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringMapAnnotatedTypedNoParam()
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

    private fun create5_normalMap_toStringTypedNoParam(): KsonItem {
        return KsonItem("5. 普通类Map序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringMapNormalTypedNoParam()
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

    private fun create6_simpleMap_toString(): KsonItem {
        return KsonItem("6. 简单类型Map序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringMapSimple()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create6_simpleMap_toStringTypedNoParam(): KsonItem {
        return KsonItem("6. 简单类型Map序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringMapSimpleTypedNoParam()
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

    private fun create7_genericAnnotated_toStringTypedNoParam(): KsonItem {
        return KsonItem("7. 泛型结果-注解类型序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomAnnotatedTypedNoParam()
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

    private fun create7_genericNormal_toStringTypedNoParam(): KsonItem {
        return KsonItem("7. 泛型结果-普通类型序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringGenericCustomNormalTypedNoParam()
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

    private fun create8_nestedList_toStringTypedNoParam(): KsonItem {
        return KsonItem("8. 嵌套泛型-列表序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringNestedGenericListTypedNoParam()
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

    private fun create10_nestedMapList_toStringTypedNoParam(): KsonItem {
        return KsonItem("10. 嵌套泛型-Map<List>序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringNestedMapListTypedNoParam()
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
        return KsonItem("10. 嵌套泛型-Map<List>从字符串反序列化 ") {
            val r = testFunc.testFromStringNestedMapList()
            temporaryView.deserialize(this, r.third, r.second?.toString())
        }
    }

    private fun create12_nestedListGeneric_toStringTypedNoParam(): KsonItem {
        return KsonItem("12. 嵌套列表-泛型结果序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringNestedListGenericTypedNoParam()
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

    private fun create13_deepNestField_toString(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段序列化 (Default)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestField()
            temporaryView.serialize(this, obj, serializedStr)
        }
    }

    private fun create13_deepNestField_toStringTypedNoParam(): KsonItem {
        return KsonItem("13. 深度嵌套-对象字段序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestFieldTypedNoParam()
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

    private fun create14_deepNestList_toStringTypedNoParam(): KsonItem {
        return KsonItem("14. 深度嵌套-列表字段序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestListTypedNoParam()
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

    private fun create15_deepNestMap_toStringTypedNoParam(): KsonItem {
        return KsonItem("15. 深度嵌套-Map字段序列化 (Typed No Param)") {
            val (obj, serializedStr) = testFunc.testToStringDeepNestMapTypedNoParam()
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

    private fun createSingleTest01() : KsonItem{
        return KsonItem("单测01：测试@JsonNames 反序列化") {
            val json1 = """{"user_name": "Alice", "user_email": "alice@test.com"}"""
            val json2 = """{"username": "Bob", "email_address": "bob@test.com"}"""
            val json3 = """{"userName": "Charlie"}"""
            val bean1 = json1.fromKson<JsonNameBean>()
            val bean2 = json2.fromKson<JsonNameBean>()
            val bean3 = json3.fromKson<JsonNameBean>()
            logdNoFile { "bean1: $bean1" }
            logdNoFile { "bean2: $bean2" }
            logdNoFile { "bean3: $bean3" }

            temporaryView.deserialize(this,
                "json1 $json1\n json2 $json2 \njson3 $json3\n",
                "bean1 $bean1 \nbean2 $bean2 \nbean3 $bean3")
        }
    }
    private fun createSingleTest02() : KsonItem{
        return KsonItem("单测02：测试@JsonNames 序列化") {
            val bean1 = JsonNameBean("Alice", "alice@test.com")
            val bean2 = JsonNameBean("Bob", "bob@test.com")
            val bean3 = JsonNameBean("Charlie")

            val jsonStr1 = bean1.toKsonString()
            val jsonStr2 = bean2.toKsonString()
            val jsonStr3 = bean3.toKsonString()

            temporaryView.serialize(this,
                "bean1 $bean1\nbean2 $bean2\nbean3 $bean3\n",
                "jsonStr1 $jsonStr1\njsonStr2 $jsonStr2\njsonStr3 $jsonStr3\n"
                )
        }
    }

    private fun createSingleTest03() : KsonItem{
        return KsonItem("单测03：测试Contextual") {
            val bean = ContextualBean("https://www.baidu.com".toUri(), "message data info.")
            try {
                val module = SerializersModule {
                    contextual(UriSerializer)
                }

                val json = Json { serializersModule = module }
                val beanStr2 = json.encodeToString(bean)
                val beanStr = ignoreError {bean.toKsonString() }
                temporaryView.serialize(this,
                    "bean $bean\n",
                    "beanStr $beanStr\nbeanStr2 $beanStr2"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createSingleTest04() : KsonItem{
        return KsonItem("单测04：必须传入serializer()") {
//            val bean1 = _1SerializableBean("icon.png/11", 100)
//            val bean2 = _1SerializableBean("icon.png/22", 101)
//            val list = listOf(bean1, bean2)
//            val str = list.toKsonString()
//            logdNoFile { "list $str" }
//            toastOnTop("list $str")

            val listType = listOf(listOf("a", "b"), listOf("c", "d"))
           // val listSerializer = ListSerializer(ListSerializer(String.serializer()))
            val jsonStr1 = kson.encodeToString(listType)
            toastOnTop("jsonStr1 $jsonStr1")
        }
    }

    private val _items = listOf(
        createSingleTest01(),
        createSingleTest02(),
        createSingleTest03(),
        createSingleTest04(),

        create1_simple_toString(),
        create1_simple_toStringTypedNoParam(),
        create1_simple_toStringLimited(),
        create1_simple_fromString(),
        create2_annotatedList_toStringDefault(),
        create2_annotatedList_toStringTypedNoParam(),
        create2_annotatedList_toStringLimited(),
        create2_annotatedList_fromString(),
        create3_normalList_toStringTypedNoParam(),
        create3_normalList_toStringDefault(),
        create3_normalList_toStringLimited(),
        create3_normalList_fromString(),

        create3_normalListStr_toStringTypedNoParam(),
        create3_normalListStr_toStringDefault(),
        create3_normalListStr_toStringLimited(),
        create3_normalListStr_fromString(),

        create4_annotatedMap_toStringTypedNoParam(),
        create4_annotatedMap_toStringDefault(),
        create4_annotatedMap_toStringIntDefault(),
        create4_annotatedMap_toStringLimited(),
        create4_annotatedMap_fromString(),
        create5_normalMap_toStringTypedNoParam(),
        create5_normalMap_toStringDefault(),
        create5_normalMap_toStringLimited(),
        create5_normalMap_fromString(),
        create6_simpleMap_toString(),
        create6_simpleMap_toStringTypedNoParam(),
        create6_simpleMap_toStringLimited(),
        create6_simpleMap_fromString(),
        create7_genericAnnotated_toStringTypedNoParam(),
        create7_genericAnnotated_toStringDefault(),
        create7_genericAnnotated_toStringLimited(),
        create7_genericAnnotated_fromString(),
        create7_genericNormal_toStringTypedNoParam(),
        create7_genericNormal_toStringDefault(),
        create7_genericNormal_toStringLimited(),
        create7_genericNormal_fromString(),
        create8_nestedList_toStringTypedNoParam(),
        create8_nestedList_toStringDefault(),
        create8_nestedList_toStringLimited(),
        create8_nestedList_fromString(),
        create10_nestedMapList_toStringTypedNoParam(),
        create10_nestedMapList_toStringDefault(),
        create10_nestedMapList_toStringLimited(),
        create10_nestedMapList_fromString(),
        create12_nestedListGeneric_toStringTypedNoParam(),
        create12_nestedListGeneric_toStringDefault(),
        create12_nestedListGeneric_toStringLimited(),
        create12_nestedListGeneric_fromString(),
        create13_deepNestField_toString(),
        create13_deepNestField_toStringTypedNoParam(),
        create13_deepNestField_toStringLimited(),
        create13_deepNestField_fromString(),
        create14_deepNestList_toString(),
        create14_deepNestList_toStringTypedNoParam(),
        create14_deepNestList_toStringLimited(),
        create14_deepNestList_fromString(),
        create15_deepNestMap_toString(),
        create15_deepNestMap_toStringTypedNoParam(),
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


class TemporarySimpleView : AbsCenterFakeDialog<KsonTemprorayViewBinding>() {
    private lateinit var mText:String

    fun popText(fragment: KsonTestFragment, text:String) {
        mText = text
        pop(fragment)
    }

    override fun onShow(activity: ComponentActivity, binding: KsonTemprorayViewBinding) {
        binding.root.onClick {
            hide()
        }
        binding.font.text = mText
    }

    override fun onHide(binding: KsonTemprorayViewBinding) {
    }

}
