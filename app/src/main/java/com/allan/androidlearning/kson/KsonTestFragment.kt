package com.allan.androidlearning.kson

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import com.allan.androidlearning.databinding.HolderKsonIteBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.json.fromJson
import com.au.module_android.json.fromKson
import com.au.module_android.json.toKsonString
import com.au.module_android.json.toKsonStringLimited
import com.au.module_android.json.toKsonStringTyped
import com.au.module_android.selectlist.SimpleItem
import com.au.module_android.selectlist.SimpleListFragment
import com.au.module_android.utils.logdNoFile
import kotlinx.serialization.builtins.serializer
import java.util.Date

class KsonItem(override val itemName: String,
               override val onItemClick: () -> Unit) : SimpleItem() {
}

@EntryFrgName(priority = 100)
class KsonFragment(
    override val title: String = "KsonTest",
) : SimpleListFragment<KsonItem>() {
    private val _items = listOf<KsonItem>(
        KsonItem("测试一个@Serializable Class和忽略字段") {
            val bean = ATransientBean(avatar = "aaaa.com", createdAt = System.currentTimeMillis())
            try {
                val str = bean.toKsonString()
                logdNoFile("🌟kson") { "toKsonString $str" }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val str = bean.toKsonStringLimited()
                logdNoFile("🌟kson") { "toKsonStringLimited $str" }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val str = bean.toKsonStringTyped(ATransientBean.serializer())
                logdNoFile("🌟kson") { "toKsonStringTyped serializer() $str" }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },

        KsonItem("测试还原@Serializable Class") {
             val json = """
                 {"avatar":"bbb.com","created_at":1765902046752, "email":"j@gmail.com"}
             """.trimIndent()

            val bean = json.fromKson<ATransientBean>()
            logdNoFile("🌟kson") { "fromKson $bean" }
        },
        )

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

            val listStr2 = list.toKsonStringTyped(String.serializer())
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
}