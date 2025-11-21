package com.au.jobstudy

import android.content.Context
import android.os.Bundle
import android.view.View
import com.au.jobstudy.databinding.FragmentEnglishCheckBinding
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.bindings.BindingNoToolbarFragment

/**
 * 英文打卡Fragment
 * 显示单词学习界面，包含单词、音标、发音和释义
 */
class EnglishCheckFragment : BindingNoToolbarFragment<FragmentEnglishCheckBinding>() {

    companion object {
        private const val ARG_WORD_INDEX = "word_index"
        private const val ARG_TOTAL_WORDS = "total_words"

        fun start(context: Context, currentIndex: Int, totalWords: Int) {
            FragmentShellActivity.Companion.start(
                context, EnglishCheckFragment::class.java,
                Bundle().apply {
                    putInt(ARG_WORD_INDEX, currentIndex)
                    putInt(ARG_TOTAL_WORDS, totalWords)
                })
        }
    }

    private var currentWordIndex = 1
    private var totalWords = 30

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取传递的参数
        arguments?.let { bundle ->
            currentWordIndex = bundle.getInt(ARG_WORD_INDEX, 1)
            totalWords = bundle.getInt(ARG_TOTAL_WORDS, 30)
        }

        // 初始化UI
        setupUi()

        // 设置点击事件
        setupClickListeners()
    }

    private fun setupUi() {
        // 更新进度显示
        updateProgress()

        // 设置默认的单词数据（实际项目中应该从网络或本地获取）
        binding.wordText.text = "accelerate"
        binding.phoneticText.text = "/ækˈseləreɪt/"
        binding.meaningText.text = "加速，促进；加快，增长"

        // TODO: 设置单词图片资源
        // binding.wordImage.setImageResource(R.drawable.word_accelerate)
    }

    private fun setupClickListeners() {
        // 返回按钮点击事件
        binding.backBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 播放按钮点击事件
        binding.playBtn.setOnClickListener {
            // TODO: 播放单词发音
            // playWordPronunciation()
        }

        // Next按钮点击事件
        binding.nextBtn.setOnClickListener {
            goToNextWord()
        }
    }

    private fun updateProgress() {
        binding.progressText.text = "$currentWordIndex/$totalWords"
    }

    private fun goToNextWord() {
        if (currentWordIndex < totalWords) {
            currentWordIndex++
            updateProgress()

            // TODO: 加载下一个单词数据
            // loadNextWord()

            // 更新单词内容（演示用）
            updateWordContent()
        } else {
            // 已完成所有单词学习
            // TODO: 跳转到完成页面或显示完成对话框
        }
    }

    private fun updateWordContent() {
        // 模拟更新单词内容（实际项目中应该从数据源获取）
        when (currentWordIndex) {
            2 -> {
                binding.wordText.text = "achievement"
                binding.phoneticText.text = "/əˈtʃiːvmənt/"
                binding.meaningText.text = "成就，成绩；达到，完成"
            }
            3 -> {
                binding.wordText.text = "accomplish"
                binding.phoneticText.text = "/əˈkʌmplɪʃ/"
                binding.meaningText.text = "完成，做成；达到目的"
            }
            else -> {
                // 循环显示示例数据
                binding.wordText.text = "accelerate"
                binding.phoneticText.text = "/ækˈseləreɪt/"
                binding.meaningText.text = "加速，促进；加快，增长"
                currentWordIndex = 1
            }
        }
    }
}