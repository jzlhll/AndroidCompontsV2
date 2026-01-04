package com.au.jobstudy.words.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import com.au.jobstudy.UiNames
import com.au.jobstudy.databinding.FragmentEnglishCheckBinding
import com.au.jobstudy.words.constants.WordsManager
import com.au.jobstudy.words.domain.TTSNative
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.namedLaunch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 英文打卡Fragment
 * 显示单词学习界面，包含单词、音标、发音和释义
 */
class EnglishCheckFragment(
    private val wordsManager : WordsManager
) : BindingFragment<FragmentEnglishCheckBinding>() {

//    private val loadingTest : LoadingTest by inject()
    companion object {
        private const val ARG_WORD_INDEX = "word_index"
        private const val ARG_TOTAL_WORDS = "total_words"

        fun start(context: Context, currentIndex: Int, totalWords: Int) {
            UiNames.ENGLISH_CHECK.namedLaunch(
                context,
                Bundle().apply {
                    putInt(ARG_WORD_INDEX, currentIndex)
                    putInt(ARG_TOTAL_WORDS, totalWords)
                })
        }
    }

    private val mViewModel: CheckViewModel by viewModel()

    private var mIsTtsing = false
    private var mTts : TTSNative? = null

    private fun tts() : TTSNative {
        if (mTts == null) {
            mTts = TTSNative().also {
                it.setOnDoneCallback {
                    binding.playBtn.setImageResource(com.au.jobstudy.R.drawable.ic_tts_play)
                    mIsTtsing = false
                }
            }
        }
        return mTts!!
    }

    private var isPlayingSentence = false
    private var currentWordIndex = 1
    private var totalWords = 30

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycle.addObserver(tts())

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
        wordsManager.allSingleWords?.let {
            val randomIndex = (Math.random() * it.size).toInt()
            val word = it[randomIndex]
            binding.wordText.text = word.word
            binding.phoneticText.text = word.phonetic
            binding.meaningText.text = word.meaning
            binding.sampleText.text = word.sentence

            binding.webView.loadDoubaoWebAndAutoFill("https://www.doubao.com/chat/create-image", word.sentence)
        }

        // TODO: 设置单词图片资源
        // binding.wordImage.setImageResource(R.drawable.word_accelerate)
    }

    private fun setupClickListeners() {
        // 返回按钮点击事件
        binding.backBtn.setOnClickListener {
            requireActivity().finishAfterTransition()
        }

        // 播放按钮点击事件
        binding.playBtn.setOnClickListener {
            if (!mIsTtsing) {
                mIsTtsing = true
                binding.playBtn.setImageResource(com.au.jobstudy.R.drawable.ic_tts_paused)

                if (isPlayingSentence) {
                    tts().speak(binding.sampleText.text.toString())
                } else {
                    tts().speak(binding.wordText.text.toString())
                }
                isPlayingSentence = !isPlayingSentence
            }
        }

        // Next按钮点击事件
        binding.nextBtn.setOnClickListener {
            goToNextWord()
        }
    }

    private fun updateProgress() {
       // binding.progressText.text = "$currentWordIndex/$totalWords"
    }

    private fun goToNextWord() {
        wordsManager.allSingleWords?.let {
            val randomIndex = (Math.random() * it.size).toInt()
            val word = it[randomIndex]
            binding.wordText.text = word.word
            binding.phoneticText.text = word.phonetic
            binding.meaningText.text = word.meaning
            binding.sampleText.text = word.sentence
        }
    }
}