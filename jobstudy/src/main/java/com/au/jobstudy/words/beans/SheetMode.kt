package com.au.jobstudy.words.beans

enum class SheetMode {
    /**
     * 单词 顺序为： 单词	音标	解释	造句
     */
    Word4,

    /**
     * 顺序为： 单词	国际音标	解释	类别	造句
     */
    Word5C,

    /**
     * 顺序为：单词	国际音标	翻译	造句	词性
     */
    Word5P,

    /**
     * 问答
     */
    Question,
    /**
     * 杂项
     */
    Mud,
}

enum class DBTableMode {
    /**
     * 单词
     */
    Word,

    /**
     * 句子
     */
    Question,

    /**
     * 杂项
     */
    Mud,
}

fun SheetMode.convert() = when (this) {
    SheetMode.Word4 -> DBTableMode.Word
    SheetMode.Word5C -> DBTableMode.Word
    SheetMode.Word5P -> DBTableMode.Word
    SheetMode.Question -> DBTableMode.Question
    SheetMode.Mud -> DBTableMode.Mud
}