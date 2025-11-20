package com.au.jobstudy.words

/**
 * 单词数据Bean
 * 包含单词的基本信息：单词、音标、解释、造句、类别
 * @param word 单词
 * @param phonetic 国际音标
 * @param meaning 解释/释义
 * @param sentence 造句/例句
 * @param category 类别（如：名词、动词、形容词等）
 */
data class WordBean(
    val word: String,
    val phonetic: String,
    val meaning: String,
    val sentence: String,
    val category: String
) {
    
    /**
     * 验证数据是否完整有效
     */
    fun isValid(): Boolean {
        return word.isNotBlank() && 
               meaning.isNotBlank() && 
               phonetic.isNotBlank()
    }
    
    /**
     * 获取简化的显示文本
     */
    fun getDisplayText(): String {
        return "$word ($phonetic) - $meaning"
    }
    
    companion object {
        /**
         * 创建空的WordBean实例
         */
        fun createEmpty(): WordBean {
            return WordBean(
                word = "",
                phonetic = "",
                meaning = "",
                sentence = "",
                category = ""
            )
        }
        
        /**
         * 创建示例WordBean实例（用于测试）
         */
        fun createSample(): WordBean {
            return WordBean(
                word = "accelerate",
                phonetic = "/ækˈseləreɪt/",
                meaning = "加速，促进；加快，增长",
                sentence = "The car can accelerate from 0 to 100 in 5 seconds.",
                category = "动词"
            )
        }
    }
}