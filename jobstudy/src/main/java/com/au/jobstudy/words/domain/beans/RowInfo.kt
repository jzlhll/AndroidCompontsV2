package com.au.jobstudy.words.domain.beans

/**
 * 抽象单行数据
 */
sealed class RowInfo() {
    companion object {
        fun parseRowHead(headRow: RowOrigData) : SheetMode {
            if (headRow.isRowHead && headRow.values.isNotEmpty()) {
                val values = headRow.values
                // 判断是否为Mud模式（3列）
                if (values.size == 3) {
                    val hasSentencePattern = values.any { it.contains("句式") }
                    val hasExample = values.any { it.contains("例句") }
                    val hasTranslation = values.any { it.contains("翻译") }
                    if (hasSentencePattern && hasExample && hasTranslation) {
                        return SheetMode.Mud
                    }
                }

                // 判断是否为Question模式
                // 检查是否包含"功能"和多个"例句"相关列
                val hasFunction = values.any { it.contains("功能") }
                val exampleCount = values.count { it.contains("例句") }
                if (hasFunction && exampleCount >= 2) {
                    return SheetMode.Question
                }
                
                // 判断是否为Word模式（包含4项以上关键字）
                val wordKeywords = listOf(
                    "单词", "词组",
                    "音标", "国际音标",
                    "翻译", "解释",
                    "造句", "例句",
                    "类别",
                    "词性"
                )

                val matchCount = values.count { value ->
                    wordKeywords.any { keyword -> value.contains(keyword) }
                }
                if (matchCount == 4 || matchCount == 5) {
                    //判断包含词性
                    val hasGrama = values.any { it.contains("词性") }
                    if (hasGrama && matchCount == 5) {
                        return SheetMode.Word5P
                    }
                    if (matchCount == 4) {
                        return SheetMode.Word4
                    }
                    return SheetMode.Word5C
                } else {
                    throw IllegalArgumentException("Invalid row head1: $headRow")
                }
            }
            
            throw IllegalArgumentException("Invalid row head2: $headRow")
        }
    }

    abstract val word:String

    abstract val sheetName:String

    /**
     * 一些空行。描述性行
     */
    data class UnableRow(override val word: String,
                         override val sheetName: String) : RowInfo() {
        override fun toString(): String {
            return "UnableRow: $word"
        }
    }

    /**
     * 杂项 直接将单行数据转换成单一显示，使用换行符拼接
     */
    data class MudRow(
        override val word: String = "",
        override val sheetName: String,
        val lines: String) : RowInfo() {
        override fun toString(): String {
            return "MudRow: $word, $lines"
        }
    }

    /**
     * 问答形式的单行数据
     * @param word 基于哪种提问
     * @param function 干什么的
     * @param sentence 句子1
     * @param sentence2 句子2
     * @param sentence3 句子3
     * @param sentence4 句子4
     * @param sentence5 句子5
     */
    data class QuestionRow(
        override val word: String,
        override val sheetName: String,
        val function: String,
        val sentence:String,
        val sentence2:String,
        val sentence3:String,
        val sentence4:String,
        val sentence5:String) : RowInfo() {
        override fun toString(): String {
            return "QuestionRow: $word, $function, $sentence"
        }
    }

    /**
     * 描述一个单词的内容数据
     * @param word 单词
     * @param phonetic 音标
     * @param meaning 单词含义
     * @param category 单词分类
     * @param sentence 句子
     * @param sentence2 句子2
     * @param sentence3 句子3
     * @param grama 词性
     * @param imageUrl 图片
     */
    data class WordRow(
        override val word: String,
        override val sheetName: String,
        val phonetic: String,
        val meaning: String,
        val category:String = "",
        val sentence:String,
        val sentence2:String = "",
        val sentence3:String = "",
        val grama : String = "",
        val imageUrl: String = "") : RowInfo() {
        override fun toString(): String {
            return "WordRow: $word, $meaning, $sentence"
        }
    }
}

