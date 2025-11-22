package com.au.jobstudy.words.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import com.au.module_android.utils.logdNoFile

class HtmlClaw(private val webView: WebView) {
    private val TAG = "HtmlClaw"

    private val handler = Handler(Looper.getMainLooper())

    private var isChecking = false

    /**
     * 外部设置
     */
    var successCallback:(Boolean)->Unit = {}

    var newFillText = ""

    private fun convertFillText() : String {
        return "输出一副卡通画300宽度，4:3的图，内容：" + newFillText
    }

    private val checkRunnable = Runnable {
        checkAndAutoFile(webView)
    }

    fun check() {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, 2000)
    }

    private fun checkAndAutoFile(webView: WebView) {
        if (isChecking) {
            return
        }

        logdNoFile { "开始遍历WebView编辑框元素..." }
        isChecking = true

        val text = convertFillText()
        val javascript = """
        (function() {
            var textbox = document.querySelector('[role="textbox"]');
            if (!textbox) return '未找到输入框';
            
            // 聚焦
            textbox.focus();
            
            // 清空内容
            if (textbox.value !== undefined) {
                textbox.value = '';
            } else {
                textbox.textContent = '';
            }
            
            // 设置完整文本
            if (textbox.value !== undefined) {
                textbox.value = '$text';
            } else {
                textbox.textContent = '$text';
            }
            
            // 触发完整的事件序列
            var events = [
                // 键盘事件
                new KeyboardEvent('keydown', { key: 'a', bubbles: true }),
                new KeyboardEvent('keypress', { key: 'a', bubbles: true }),
                
                // 输入事件
                new InputEvent('beforeinput', { bubbles: true, inputType: 'insertText', data: '$text' }),
                new InputEvent('input', { bubbles: true, inputType: 'insertText', data: '$text' }),
                
                // 更多键盘事件
                new KeyboardEvent('keyup', { key: 'a', bubbles: true }),
                
                // 其他相关事件
                new Event('change', { bubbles: true }),
                new Event('blur', { bubbles: true })
            ];
            
            events.forEach(function(event) {
                textbox.dispatchEvent(event);
            });
            
            // 额外的 DOM 事件
            var mutationEvent = new Event('DOMCharacterDataModified', { bubbles: true });
            textbox.dispatchEvent(mutationEvent);
            
            return '批量输入完成';
        })()
    """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            logdNoFile { "WebView遍历完成...$result" }
        }
    }
}