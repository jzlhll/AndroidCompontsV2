package com.au.module_android.log;

import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/** 日志内容格式化与输出，复用类名解析结果以减少高频日志的分配。 */
public final class ALogJ {
    private static final int JSON_INDENT = 2;
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final ConcurrentHashMap<Class<?>, ClassName> CLASS_NAMES = new ConcurrentHashMap<>();

    /** 同时保留线程日志的完整短类名与普通日志的协程缩写。 */
    private static final class ClassName {
        final String simpleName;
        final String prefix;

        ClassName(Class<?> javaClass) {
            // 仅在缓存未命中时解析，保留无包名类、接口与数组的原有显示方式。
            String name = javaClass.toString();
            int start = name.lastIndexOf('.') + 1;
            simpleName = name.substring(start);
            int firstDollar = name.indexOf('$', start);
            int lastDollar = name.lastIndexOf('$');
            if (firstDollar >= 0 && lastDollar > firstDollar) {
                int secondLastDollar = name.lastIndexOf('$', lastDollar - 1);
                prefix = name.substring(start, firstDollar) +
                        ".." +
                        name.substring(secondLastDollar + 1, lastDollar);
            } else {
                prefix = simpleName;
            }
        }
    }

    private static ClassName className(Class<?> javaClass) {
        ClassName cached = CLASS_NAMES.get(javaClass);
        if (cached != null) return cached;

        ClassName parsed = new ClassName(javaClass);
        ClassName existing = CLASS_NAMES.putIfAbsent(javaClass, parsed);
        return existing != null ? existing : parsed;
    }

    public static String log(String lvl, String s) {
        return lvl + ": " + s;
    }

    public static String log(String s, Class<?> javaClass) {
        return className(javaClass).prefix + ": " + s;
    }

    public static String log(String lvl, String s, Class<?> javaClass) {
        return lvl + " " + className(javaClass).prefix + ": " + s;
    }

    public static String logThread(String s, Class<?> javaClass) {
        Thread thread = Thread.currentThread();
        String name = className(javaClass).simpleName;
        if (thread == Looper.getMainLooper().getThread()) {
            return name + " MainThread: " + s;
        } else {
            long id = thread.getId();
            return name + " SubThread[" + (id < 10 ? "0" : "") + id + "]: " + s;
        }
    }

    public static void t(String s) {
        t(LogTag.TAG, s);
    }

    public static void t(String tag, String s) {
        if (!ALogKt.getLogDebugEnabled()) return;
        Thread thread = Thread.currentThread();
        if (thread == Looper.getMainLooper().getThread()) {
            Log.d(tag," MainThread: " + s);
        } else {
            Log.d(tag," SubThread" + thread.getId() + ": " + s);
        }
    }

    public static String log(String lvl, String s, String tag, Class<?> javaClass) {
        return lvl + " " + className(javaClass).prefix + ": " + tag + ": " + s;
    }

    public static String ex(Throwable e) {
        StringBuilder sb = new StringBuilder(256);
        var msg = e.getMessage();
        if(msg != null && !msg.isEmpty()) sb.append(msg).append(LINE_SEPARATOR);
        var cause = e.getCause();
        if(cause != null) sb.append(cause).append(LINE_SEPARATOR);

        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element).append(LINE_SEPARATOR);
        }

        return sb.toString();
    }

    public void json(@Nullable String json) {
        if (!ALogKt.getLogDebugEnabled()) return;
        if (TextUtils.isEmpty(json)) {
            Log.d(LogTag.TAG, "Empty/Null json content");
            return;
        }
        try {
            json = json.trim();
            char first = json.isEmpty() ? '\0' : json.charAt(0);
            if (first == '{') {
                JSONObject jsonObject = new JSONObject(json);
                String message = jsonObject.toString(JSON_INDENT);
                Log.d(LogTag.TAG, message);
                return;
            }
            if (first == '[') {
                JSONArray jsonArray = new JSONArray(json);
                String message = jsonArray.toString(JSON_INDENT);
                Log.d(LogTag.TAG, message);
                return;
            }
            Log.e(LogTag.TAG, "Invalid Json");
        } catch (JSONException e) {
            Log.e(LogTag.TAG, "Invalid Json");
        }
    }

    public void xml(@Nullable String xml) {
        if (!ALogKt.getLogDebugEnabled()) return;
        if (TextUtils.isEmpty(xml)) {
            Log.d(LogTag.TAG, "Empty/Null xml content");
            return;
        }
        try {
            StreamSource xmlInput = new StreamSource(new StringReader(xml));
            StringWriter writer = new StringWriter(xml.length());
            StreamResult xmlOutput = new StreamResult(writer);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(xmlInput, xmlOutput);
            StringBuffer buffer = writer.getBuffer();
            int firstTagEnd = buffer.indexOf(">");
            if (firstTagEnd >= 0) buffer.insert(firstTagEnd + 1, '\n');
            Log.d(LogTag.TAG, buffer.toString());
        } catch (TransformerException e) {
            Log.e(LogTag.TAG, "Invalid xml");
        }
    }
}
