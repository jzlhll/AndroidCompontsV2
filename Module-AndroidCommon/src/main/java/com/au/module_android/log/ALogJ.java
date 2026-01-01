package com.au.module_android.log;

import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.au.module_android.BuildConfig;
import com.au.module_android.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public final class ALogJ {
    public static String logFileMask = Globals.INSTANCE.getGoodFilesDir().getAbsolutePath() + File.separator + "logFileMask";

    public static final boolean ALWAYS_FILE_LOG = BuildConfig.ENABLE_FILE_LOG_DEFAULT || new File(logFileMask).exists();
    public static final String TAG = "au";
    private static final int JSON_INDENT = 2;

    public static String log(String lvl, String s, Class<?> javaClass) {
        var log = javaClass.toString();
        var prefix = log.substring(log.lastIndexOf('.') + 1);
        int firstDollar = prefix.indexOf('$');
        if (firstDollar >= 0) {
            int lastDollar = prefix.lastIndexOf('$');
            if (lastDollar > firstDollar) {
                int secondLastDollar = prefix.lastIndexOf('$', lastDollar - 1);
                var head = prefix.substring(0, firstDollar);
                var tail = prefix.substring(secondLastDollar + 1, lastDollar);
                prefix = head + ".." + tail;
            }
        }
        return lvl + " " + prefix + ": " + s;
    }

    public static String logThread(String s, Class<?> javaClass) {
        var id = Thread.currentThread().getId();
        var log = javaClass.toString();
        var className = log.substring(log.lastIndexOf('.') + 1);
        if (id == Looper.getMainLooper().getThread().getId()) {
            return className + " MainThread: " + s;
        } else {
            return String.format(Locale.ROOT, className + " SubThread[%02d]: %s", id, s);
        }
    }

    public static void t(String s) {
        t(TAG, s);
    }

    public static void t(String tag, String s) {
        var id = Thread.currentThread().getId();
        if (id == Looper.getMainLooper().getThread().getId()) {
            Log.d(tag," MainThread: " + s);
        } else {
            Log.d(tag," SubThread" + id + ": " + s);
        }
    }

    public static String log(String lvl, String s, String tag, Class<?> javaClass) {
        var log = javaClass.toString();
        var prefix = log.substring(log.lastIndexOf('.') + 1);
        int firstDollar = prefix.indexOf('$');
        if (firstDollar >= 0) {
            int lastDollar = prefix.lastIndexOf('$');
            if (lastDollar > firstDollar) {
                int secondLastDollar = prefix.lastIndexOf('$', lastDollar - 1);
                var head = prefix.substring(0, firstDollar);
                var tail = prefix.substring(secondLastDollar + 1, lastDollar);
                prefix = head + ".." + tail;
            }
        }
        return lvl + " " + prefix + ": " + tag + ": " + s;
    }

    public static String ex(Throwable e) {
        StringBuilder sb = new StringBuilder();
        var msg = e.getMessage();
        if(msg != null && !msg.isEmpty()) sb.append(msg).append(System.lineSeparator());
        var cause = e.getCause();
        if(cause != null) sb.append(cause).append(System.lineSeparator());

        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append(System.lineSeparator());
        }

        return sb.toString();
    }

    public void json(@Nullable String json) {
        if (TextUtils.isEmpty(json)) {
            Log.d(TAG, "Empty/Null json content");
            return;
        }
        try {
            json = json.trim();
            if (json.startsWith("{")) {
                JSONObject jsonObject = new JSONObject(json);
                String message = jsonObject.toString(JSON_INDENT);
                Log.d(TAG, message);
                return;
            }
            if (json.startsWith("[")) {
                JSONArray jsonArray = new JSONArray(json);
                String message = jsonArray.toString(JSON_INDENT);
                Log.d(TAG, message);
                return;
            }
            Log.e(TAG, "Invalid Json");
        } catch (JSONException e) {
            Log.e(TAG, "Invalid Json");
        }
    }

    public void xml(@Nullable String xml) {
        if (TextUtils.isEmpty(xml)) {
            Log.d(TAG, "Empty/Null xml content");
            return;
        }
        try {
            Source xmlInput = new StreamSource(new StringReader(xml));
            StreamResult xmlOutput = new StreamResult(new StringWriter());
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(xmlInput, xmlOutput);
            Log.d(TAG, xmlOutput.getWriter().toString().replaceFirst(">", ">\n"));
        } catch (TransformerException e) {
            Log.e(TAG, "Invalid xml");
        }
    }
}