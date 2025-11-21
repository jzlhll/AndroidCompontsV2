package com.au.jobstudy

import android.content.Context
import android.os.Bundle
import android.view.View
import com.au.jobstudy.databinding.FragmentEnglishCheckBinding
import com.au.jobstudy.words.WordsManager
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
        //todo 先random
        WordsManager.allSingleWords?.let {
            val randomIndex = (Math.random() * it.size).toInt()
            val word = it[randomIndex]
            binding.wordText.text = word.word
            binding.phoneticText.text = word.phonetic
            binding.meaningText.text = word.meaning
        }

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
        WordsManager.allSingleWords?.let {
            val randomIndex = (Math.random() * it.size).toInt()
            val word = it[randomIndex]
            binding.wordText.text = word.word
            binding.phoneticText.text = word.phonetic
            binding.meaningText.text = word.meaning
        }
    }
}