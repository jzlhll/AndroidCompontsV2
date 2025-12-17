package com.allan.androidlearning.kson

import com.au.module_android.json.*
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.logdNoFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

class JsonUtilTestFunctions {

    // # 1. 简单对象 _1SerializableBean
    fun testToStringSimple(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
            codeStr = """
                val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringSimpleTyped(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
            codeStr = """
                val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
                val str = bean.toKsonStringTyped(_1SerializableBean.serializer())
            """.trimIndent()
            val str = bean.toKsonStringTyped(_1SerializableBean.serializer())
            logdNoFile("🌟kson") { "toKsonStringTyped $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringSimpleLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
            codeStr = """
                val bean = _1SerializableBean("avatar_url", 123456789L, "test@email.com")
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringSimple(): Triple<Boolean, Any?, String> {
        val json = """
                    {"avatar":"avatar_url","created_at":123456789, "email":"test@email.com"}
                """.trimIndent()
        val codeStr = """
            $json
            json.fromKson<_1SerializableBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<_1SerializableBean>() }
        logdNoFile("🌟kson") { "fromKson $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 2. 列表-注解类 List<_1SerializableBean>
    fun testToStringListAnnotatedDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
            codeStr = """
                val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
                val str = list.toKsonString()
            """.trimIndent()
            val str = list.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Annotated List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringListAnnotated(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
            codeStr = """
                val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
                val str = list.lisToKsonStringTyped(_1SerializableBean.serializer())
            """.trimIndent()
            val str = list.lisToKsonStringTyped(_1SerializableBean.serializer())
            logdNoFile("🌟kson") { "lisToKsonStringTyped Annotated $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringListAnnotatedLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
            codeStr = """
                val list = listOf(_1SerializableBean("a1", 1L, "e1"), _1SerializableBean("a2", 2L, "e2"))
                val str = (list as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (list as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Annotated List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringListAnnotated(): Triple<Boolean, Any?, String> {
        val json = """[{"avatar":"a1","created_at":1,"email":"e1"},{"avatar":"a2","created_at":2,"email":"e2"}]"""
        val codeStr = """
            $json
            json.fromKson<List<_1SerializableBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<List<_1SerializableBean>>() }
        logdNoFile("🌟kson") { "fromKson List Annotated $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringListAnnotated2(): Triple<Boolean, Any?, String> {
        val json = """[{"avatar":"a1","created_at":1,"email":"e1"},{"avatar":"a2","created_at":2,"email":"e2"}]"""
        val codeStr = """
            $json
            json.fromKsonList<_1SerializableBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonList<_1SerializableBean>() }
        logdNoFile("🌟kson") { "fromKsonList Annotated $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 3. 列表-普通类 List<_2NormalBean>
    fun testToStringListNormal(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_2NormalBean("n1", 10L, "ne1"))
            codeStr = """
                val list = listOf(_2NormalBean("n1", 10L, "ne1"))
                val str = list.lisToKsonStringTyped(serializer<_2NormalBean>())
            """.trimIndent()
            val str = list.lisToKsonStringTyped(serializer<_2NormalBean>())
            logdNoFile("🌟kson") { "lisToKsonStringTyped Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "lisToKsonStringTyped Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringListNormalDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_2NormalBean("n1", 10L, "ne1"))
            codeStr = """
                val list = listOf(_2NormalBean("n1", 10L, "ne1"))
                val str = list.toKsonString()
            """.trimIndent()
            val str = list.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Normal List $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonString Normal List failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringListNormalLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_2NormalBean("n1", 10L, "ne1"))
            codeStr = """
                val list = listOf(_2NormalBean("n1", 10L, "ne1"))
                val str = (list as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (list as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Normal List $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonStringLimited Normal List failed: $e" }
            return codeStr to e.message
        }
    }

    fun testFromStringListNormal(): Triple<Boolean, Any?, String> {
        val json = """[{"avatar":"n1","createdAt":10,"email":"ne1"}]"""
        val codeStr = """
            $json
            json.fromKson<List<_2NormalBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<List<_2NormalBean>>() }
        logdNoFile("🌟kson") { "fromKson List Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringListNormal2(): Triple<Boolean, Any?, String> {
        val json = """[{"avatar":"n1","createdAt":10,"email":"ne1"}]"""
        val codeStr = """
            $json
            json.fromKsonList<_2NormalBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonList<_2NormalBean>() }
        logdNoFile("🌟kson") { "fromKsonList Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }
    /////3.1 列表-普通String List
    fun testToStringListStrNormal(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
            codeStr = """
                val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
                val str = list.lisToKsonStringTyped(serializer<String>())
            """.trimIndent()
            val str = list.lisToKsonStringTyped(serializer<Any>())
            logdNoFile("🌟kson") { "lisToKsonStringTyped Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "lisToKsonStringTyped Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringListStrNormalDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
            codeStr = """
                val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
                val str = list.toKsonString()
            """.trimIndent()
            val str = list.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Normal List $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonString Normal List failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringListStrNormalLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
            codeStr = """
                val list:List<Any> = listOf("aa", "bb", 100, true, 'c')
                val str = (list as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (list as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Normal List $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonStringLimited Normal List failed: $e" }
            return codeStr to e.message
        }
    }

    fun testFromStringListStrNormal(): Triple<Boolean, Any?, String> {
        val json = """["aa", "b", "ccc"]"""
        val codeStr = """
            $json
            json.fromKson<List<String>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<List<String>>() }
        logdNoFile("🌟kson") { "fromKson List Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 4. Map-注解类 Map<String, _1SerializableBean>
    fun testToStringMapAnnotated(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
            codeStr = """
                val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
                val str = map.mapToKsonStringTyped(String.serializer(), _1SerializableBean.serializer())
            """.trimIndent()
            val str = map.mapToKsonStringTyped(String.serializer(), _1SerializableBean.serializer())
            logdNoFile("🌟kson") { "mapToKsonStringTyped Annotated $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringMapAnnotatedDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
            codeStr = """
                val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
                val str = map.toKsonString()
            """.trimIndent()
            val str = map.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Annotated Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringMapAnnotatedIntDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf(100 to _1SerializableBean("ma1", 11L, "me1"))
            codeStr = """
                val map = mapOf(100 to _1SerializableBean("ma1", 11L, "me1"))
                val str = map.toKsonString()
            """.trimIndent()
            val str = map.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Annotated Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringMapAnnotatedLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
            codeStr = """
                val map = mapOf("k1" to _1SerializableBean("ma1", 11L, "me1"))
                val str = map.toKsonStringLimited()
            """.trimIndent()
            val str = map.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Annotated Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringMapAnnotated(): Triple<Boolean, Any?, String> {
        val json = """{"k1":{"avatar":"ma1","created_at":11,"email":"me1"}}"""
        val codeStr = """
            $json
            json.fromKson<Map<String, _1SerializableBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<Map<String, _1SerializableBean>>() }
        logdNoFile("🌟kson") { "fromKson Map Annotated $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringMapAnnotated2(): Triple<Boolean, Any?, String> {
        val json = """{"k1":{"avatar":"ma1","created_at":11,"email":"me1"}}"""
        val codeStr = """
            $json
            json.fromKsonMap<String, _1SerializableBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonMap<String, _1SerializableBean>() }
        logdNoFile("🌟kson") { "fromKsonMap Annotated $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 5. Map-普通类 Map<String, _2NormalBean>
    fun testToStringMapNormal(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
            codeStr = """
                val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
                val str = map.mapToKsonStringTyped(String.serializer(), serializer<_2NormalBean>())
            """.trimIndent()
            val str = map.mapToKsonStringTyped(String.serializer(), serializer<_2NormalBean>())
            logdNoFile("🌟kson") { "mapToKsonStringTyped Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "mapToKsonStringTyped Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringMapNormalDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
            codeStr = """
                val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
                val str = map.toKsonString()
            """.trimIndent()
            val str = map.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Normal Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonString Normal Map failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringMapNormalLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
            codeStr = """
                val map = mapOf("k2" to _2NormalBean("mn2", 22L, "mne2"))
                val str = (map as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (map as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Normal Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonStringLimited Normal Map failed: $e" }
            return codeStr to e.message
        }
    }

    fun testFromStringMapNormal(): Triple<Boolean, Any?, String> {
        val json = """{"k2":{"avatar":"mn2","createdAt":22,"email":"mne2"}}"""
        val codeStr = """
            $json
            json.fromKson<Map<String, _2NormalBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<Map<String, _2NormalBean>>() }
        logdNoFile("🌟kson") { "fromKson Map Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringMapNormal2(): Triple<Boolean, Any?, String> {
        val json = """{"k2":{"avatar":"mn2","createdAt":22,"email":"mne2"}}"""
        val codeStr = """
            $json
            json.fromKsonMap<String, _2NormalBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonMap<String, _2NormalBean>() }
        logdNoFile("🌟kson") { "fromKsonMap Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 6. Map-简单类型 Map<String, Any?>
    fun testToStringMapSimple(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("key1" to "value1", "key2" to 123)
            codeStr = """
                val map = mapOf("key1" to "value1", "key2" to 123)
                val str = map.toKsonString()
            """.trimIndent()
            val str = map.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Map Simple $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringMapSimpleTyped(): Pair<String, String?> {
        // Any is complex for Typed, use String value for demo
        var codeStr = ""
        try {
            val map = mapOf("key1" to "value1", "key2" to "value2")
            codeStr = """
                val map = mapOf("key1" to "value1", "key2" to "value2")
                val str = map.mapToKsonStringTyped(String.serializer(), String.serializer())
            """.trimIndent()
            val str = map.mapToKsonStringTyped(String.serializer(), String.serializer())
            logdNoFile("🌟kson") { "mapToKsonStringTyped Map Simple (String only) $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "mapToKsonStringTyped Map Simple failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringMapSimpleLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("key1" to "value1", "key2" to 123)
            codeStr = """
                val map = mapOf("key1" to "value1", "key2" to 123)
                val str = (map as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (map as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Map Simple $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringMapSimple(): Triple<Boolean, Any?, String> {
        val json = """{"key1":"value1","key2":123}"""
        val codeStr = """
            $json
            json.fromKson<Map<String, Any?>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<Map<String, Any?>>() }
        logdNoFile("🌟kson") { "fromKson Map Simple $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringMapSimple2(): Triple<Boolean, Any?, String> {
        val json = """{"key1":"value1","key2":123}"""
        val codeStr = """
            $json
            json.fromKsonMap<String, Any?>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonMap<String, Any?>() }
        logdNoFile("🌟kson") { "fromKsonMap Simple $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 7. 自定义泛型类 BaseResultBean<T> (T=_1SerializableBean)
    fun testToStringGenericCustomAnnotated(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
                val str = bean.toKsonStringTyped(BaseResultBean.serializer(_1SerializableBean.serializer()))
            """.trimIndent()
            val str = bean.toKsonStringTyped(BaseResultBean.serializer(_1SerializableBean.serializer()))
            logdNoFile("🌟kson") { "toKsonStringTyped Generic Annotated $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringGenericCustomAnnotatedDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Generic Annotated $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringGenericCustomAnnotatedLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("g1", 100L, "ge1"))
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Generic Annotated $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringGenericCustomAnnotated(): Triple<Boolean, Any?, String> {
        val json = """{"code":"0","message":"ok","status":true,"data":{"avatar":"g1","created_at":100,"email":"ge1"}}"""
        val codeStr = """
            $json
            json.fromKson<BaseResultBean<_1SerializableBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<BaseResultBean<_1SerializableBean>>() }
        logdNoFile("🌟kson") { "fromKson Generic Annotated $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 7. 自定义泛型类 BaseResultBean<T> (T=_2NormalBean)
    fun testToStringGenericCustomNormal(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
                val str = bean.toKsonStringTyped(BaseResultBean.serializer(serializer<_2NormalBean>()))
            """.trimIndent()
            val str = bean.toKsonStringTyped(BaseResultBean.serializer(serializer<_2NormalBean>()))
            logdNoFile("🌟kson") { "toKsonStringTyped Generic Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonStringTyped Generic Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringGenericCustomNormalDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Generic Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonString Generic Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testToStringGenericCustomNormalLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _2NormalBean("gn1", 200L, "gne1"))
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Generic Normal $str" }
            return codeStr to str
        } catch (e: Exception) {
            logdNoFile("🌟kson") { "toKsonStringLimited Generic Normal failed: $e" }
            return codeStr to e.message
        }
    }

    fun testFromStringGenericCustomNormal(): Triple<Boolean, Any?, String> {
        val json = """{"code":"0","message":"ok","status":true,"data":{"avatar":"gn1","createdAt":200,"email":"gne1"}}"""
        val codeStr = """
            $json
            json.fromKson<BaseResultBean<_2NormalBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<BaseResultBean<_2NormalBean>>() }
        logdNoFile("🌟kson") { "fromKson Generic Normal $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 8. 嵌套泛型结构 BaseResultBean<List<_1SerializableBean>>
    fun testToStringNestedGenericList(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
            val bean = BaseResultBean("0", "ok", true, list)
            codeStr = """
                val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
                val bean = BaseResultBean("0", "ok", true, list)
                val str = bean.toKsonStringTyped(BaseResultBean.serializer(ListSerializer(_1SerializableBean.serializer())))
            """.trimIndent()
            val str = bean.toKsonStringTyped(BaseResultBean.serializer(ListSerializer(_1SerializableBean.serializer())))
            logdNoFile("🌟kson") { "toKsonStringTyped Nested List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedGenericListDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
            val bean = BaseResultBean("0", "ok", true, list)
            codeStr = """
                val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
                val bean = BaseResultBean("0", "ok", true, list)
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Nested List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedGenericListLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
            val bean = BaseResultBean("0", "ok", true, list)
            codeStr = """
                val list = listOf(_1SerializableBean("ng1", 300L, "nge1"))
                val bean = BaseResultBean("0", "ok", true, list)
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Nested List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringNestedGenericList(): Triple<Boolean, Any?, String> {
        val json = """{"code":"0","message":"ok","status":true,"data":[{"avatar":"ng1","created_at":300,"email":"nge1"}]}"""
        val codeStr = """
            $json
            json.fromKson<BaseResultBean<List<_1SerializableBean>>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<BaseResultBean<List<_1SerializableBean>>>() }
        logdNoFile("🌟kson") { "fromKson Nested List $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 10. 嵌套泛型结构 Map<String, List<_1SerializableBean>>
    fun testToStringNestedMapList(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
            val map = mapOf("key_nm" to list)
            codeStr = """
                val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
                val map = mapOf("key_nm" to list)
                val str = map.mapToKsonStringTyped(String.serializer(), ListSerializer(_1SerializableBean.serializer()))
            """.trimIndent()
            val str = map.mapToKsonStringTyped(String.serializer(), ListSerializer(_1SerializableBean.serializer()))
            logdNoFile("🌟kson") { "mapToKsonStringTyped Nested MapList $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedMapListDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
            val map = mapOf("key_nm" to list)
            codeStr = """
                val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
                val map = mapOf("key_nm" to list)
                val str = map.toKsonString()
            """.trimIndent()
            val str = map.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Nested MapList $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedMapListLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
            val map = mapOf("key_nm" to list)
            codeStr = """
                val list = listOf(_1SerializableBean("nm1", 400L, "nme1"))
                val map = mapOf("key_nm" to list)
                val str = (map as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (map as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Nested MapList $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringNestedMapList(): Triple<Boolean, Any?, String> {
        val json = """{"key_nm":[{"avatar":"nm1","created_at":400,"email":"nme1"}]}"""
        val codeStr = """
            $json
            json.fromKson<Map<String, List<_1SerializableBean>>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<Map<String, List<_1SerializableBean>>>() }
        logdNoFile("🌟kson") { "fromKson Nested MapList $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringNestedMapList2(): Triple<Boolean, Any?, String> {
        val json = """{"key_nm":[{"avatar":"nm1","created_at":400,"email":"nme1"}]}"""
        val codeStr = """
            $json
            json.fromKsonMap<String, List<_1SerializableBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonMap<String, List<_1SerializableBean>>() }
        logdNoFile("🌟kson") { "fromKsonMap Nested MapList $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 12. 嵌套泛型结构 List<BaseResultBean<_1SerializableBean>>
    fun testToStringNestedListGeneric(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
            val list = listOf(bean)
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
                val list = listOf(bean)
                val str = list.lisToKsonStringTyped(BaseResultBean.serializer(_1SerializableBean.serializer()))
            """.trimIndent()
            val str = list.lisToKsonStringTyped(BaseResultBean.serializer(_1SerializableBean.serializer()))
            logdNoFile("🌟kson") { "lisToKsonStringTyped Nested ListGeneric $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedListGenericDefault(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
            val list = listOf(bean)
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
                val list = listOf(bean)
                val str = list.toKsonString()
            """.trimIndent()
            val str = list.toKsonString()
            logdNoFile("🌟kson") { "toKsonString Nested ListGeneric $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringNestedListGenericLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
            val list = listOf(bean)
            codeStr = """
                val bean = BaseResultBean("0", "ok", true, _1SerializableBean("lg1", 500L, "lge1"))
                val list = listOf(bean)
                val str = (list as Any).toKsonStringLimited()
            """.trimIndent()
            val str = (list as Any).toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited Nested ListGeneric $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringNestedListGeneric(): Triple<Boolean, Any?, String> {
        val json = """[{"code":"0","message":"ok","status":true,"data":{"avatar":"lg1","created_at":500,"email":"lge1"}}]"""
        val codeStr = """
            $json
            json.fromKson<List<BaseResultBean<_1SerializableBean>>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<List<BaseResultBean<_1SerializableBean>>>() }
        logdNoFile("🌟kson") { "fromKson Nested ListGeneric $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    fun testFromStringNestedListGeneric2(): Triple<Boolean, Any?, String> {
        val json = """[{"code":"0","message":"ok","status":true,"data":{"avatar":"lg1","created_at":500,"email":"lge1"}}]"""
        val codeStr = """
            $json
            json.fromKsonList<BaseResultBean<_1SerializableBean>>()
        """.trimIndent()
        val bean = ignoreError { json.fromKsonList<BaseResultBean<_1SerializableBean>>() }
        logdNoFile("🌟kson") { "fromKsonList Nested ListGeneric $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 13. 深度嵌套结构 _3SerializableNestBean 内部包含一个字段 _1SerializableBean
    fun testToStringDeepNestField(): Pair<String, String?> {
        var codeStr = ""
        try {
            val inner = _1SerializableBean("dn1", 600L, "dne1")
            val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
            codeStr = """
                val inner = _1SerializableBean("dn1", 600L, "dne1")
                val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString DeepNest Field $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestFieldTyped(): Pair<String, String?> {
        var codeStr = ""
        try {
            val inner = _1SerializableBean("dn1", 600L, "dne1")
            val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
            codeStr = """
                val inner = _1SerializableBean("dn1", 600L, "dne1")
                val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
                val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            """.trimIndent()
            val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            logdNoFile("🌟kson") { "toKsonStringTyped DeepNest Field $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestFieldLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val inner = _1SerializableBean("dn1", 600L, "dne1")
            val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
            codeStr = """
                val inner = _1SerializableBean("dn1", 600L, "dne1")
                val bean = _3SerializableNestBean(outInfo = "out1", serverTime = 999L, data = inner)
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited DeepNest Field $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringDeepNestField(): Triple<Boolean, Any?, String> {
        val json = """{"outInfo":"out1","server_time":999,"data":{"avatar":"dn1","created_at":600,"email":"dne1"}}"""
        val codeStr = """
            $json
            json.fromKson<_3SerializableNestBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<_3SerializableNestBean>() }
        logdNoFile("🌟kson") { "fromKson DeepNest Field $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 14. 深度嵌套结构 _3SerializableNestBean 内部包含一个字段 List<_1SerializableBean>
    fun testToStringDeepNestList(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
            val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
            codeStr = """
                val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
                val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString DeepNest List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestListTyped(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
            val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
            codeStr = """
                val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
                val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
                val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            """.trimIndent()
            val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            logdNoFile("🌟kson") { "toKsonStringTyped DeepNest List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestListLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
            val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
            codeStr = """
                val list = listOf(_1SerializableBean("dnl1", 700L, "dnle1"))
                val bean = _3SerializableNestBean(outInfo = "out2", serverTime = 888L, dataList = list)
                val str = bean.toKsonStringLimited()
            """.trimIndent()
            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited DeepNest List $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringDeepNestList(): Triple<Boolean, Any?, String> {
        val json = """{"outInfo":"out2","server_time":888,"dataList":[{"avatar":"dnl1","created_at":700,"email":"dnle1"}]}"""
        val codeStr = """
            $json
            json.fromKson<_3SerializableNestBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<_3SerializableNestBean>() }
        logdNoFile("🌟kson") { "fromKson DeepNest List $bean" }
        return Triple(bean != null, bean, codeStr)
    }

    // # 15. 深度嵌套结构 _3SerializableNestBean 内部包含一个字段 Map<String, _1SerializableBean>
    fun testToStringDeepNestMap(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
            val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)
            codeStr = """
                val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
                val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)
                val str = bean.toKsonString()
            """.trimIndent()
            val str = bean.toKsonString()
            logdNoFile("🌟kson") { "toKsonString DeepNest Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestMapTyped(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
            val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)
            codeStr = """
                val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
                val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)
                val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            """.trimIndent()
            val str = bean.toKsonStringTyped(_3SerializableNestBean.serializer())
            logdNoFile("🌟kson") { "toKsonStringTyped DeepNest Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testToStringDeepNestMapLimited(): Pair<String, String?> {
        var codeStr = ""
        try {
            val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
            val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)

            codeStr = """
                    val map = mapOf("k_dnm" to _1SerializableBean("dnm1", 800L, "dnme1"))
                    val bean = _3SerializableNestBean(outInfo = "out3", serverTime = 777L, dataMap = map)
                    val str = bean.toKsonStringLimited()
            """.trimIndent()

            val str = bean.toKsonStringLimited()
            logdNoFile("🌟kson") { "toKsonStringLimited DeepNest Map $str" }
            return codeStr to str
        } catch (e: Exception) {
            e.printStackTrace()
            return codeStr to e.message
        }
    }

    fun testFromStringDeepNestMap(): Triple<Boolean, Any?, String> {
        val json = """{"outInfo":"out3","server_time":777,"dataMap":{"k_dnm":{"avatar":"dnm1","created_at":800,"email":"dnme1"}}}"""
        val codeStr = """
            $json
            json.fromKson<_3SerializableNestBean>()
        """.trimIndent()
        val bean = ignoreError { json.fromKson<_3SerializableNestBean>() }
        logdNoFile("🌟kson") { "fromKson DeepNest Map $bean" }
        return Triple(bean != null, bean, codeStr)
    }
}
