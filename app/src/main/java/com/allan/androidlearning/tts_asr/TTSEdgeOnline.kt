package com.allan.androidlearning.tts_asr

import com.au.module_android.Globals
import com.au.module_android.log.logd
import io.github.whitemagic2014.tts.TTS
import io.github.whitemagic2014.tts.TTSVoice
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat

/**
 * https://github.com/WhiteMagic2014/tts-edge-java
 */
class TTSEdgeOnline : ITts {
    companion object Companion {
        /**
        https://github.com/WhiteMagic2014/tts-edge-java/blob/master/src/main/resources/voicesList.json
        en-US-AvaMultilingualNeural
        en-US-AndrewMultilingualNeural
        en-US-EmmaMultilingualNeural
        en-US-BrianMultilingualNeural
        en-US-AvaNeural
        en-US-AndrewNeural
        en-US-EmmaNeural
        en-US-BrianNeural
         */
        private const val VOICE_FEMALE_NAME = "en-US-EmmaMultilingualNeural"
        private const val VOICE_MALE_NAME =   "en-US-AndrewMultilingualNeural"

        private const val PATH_NAME = "tts"

        private val timeFmt = SimpleDateFormat("HHmmss")

        private fun buildFilename(isFemale: Boolean): String {
            val ts = timeFmt.format(System.currentTimeMillis())
            return if (isFemale) {
                "$VOICE_FEMALE_NAME-tts-$ts"
            } else {
                "$VOICE_MALE_NAME-tts-$ts"
            }
        }
    }

    private fun baseDir() : File {
        val root = File(Globals.goodCacheDir.absolutePath, PATH_NAME)
        return if (!root.exists()) {
            root.mkdirs()
            root
        } else {
            root
        }
    }

    private var mDoneCb: () -> Unit = {}
    private var mPlayer: MyMediaPlayer? = null

    override fun init() {
        mPlayer = MyMediaPlayer()
        mPlayer?.setOnCompleteCallback {
            logd { "edgeTTS play completed!" }
            mDoneCb()
        }

        val files = baseDir().listFiles()
        Globals.mainScope.launch {
            files?.forEach {
                if(it.exists() && it.isFile) it.delete()
            }
        }
    }

    override fun stop() {
        mPlayer?.stop()
    }

    override fun destroy() {
        mPlayer?.release()
    }

    override fun speak(text: String) {
        singleContentSpeak(text)
    }

    override fun setOnDoneCallback(cb: () -> Unit) {
        mDoneCb = cb
    }

    fun singleContentSpeak(content:String) {
        logd { "edgeTTS request1: $content" }
        val voice = TTSVoice.provides()?.first { v -> v.shortName == VOICE_FEMALE_NAME }

        val filename = buildFilename(true)
        val tts = TTS(voice, content)
            .findHeadHook()
            .storage(baseDir().absolutePath)
            .isRateLimited(true) // Set to true to resolve the rate limiting issue in certain regions.
            .fileName(filename) // You can customize the file name; if omitted, a random file name will be generated.
            .overwrite(true) // When the specified file name is the same, it will either overwrite or append to the file.
            .formatMp3() // default mp3.
        //                .formatOpus() // or opus
//                .voicePitch()
//                .voiceRate()
//                .voiceVolume()
//                .storage()  // the output file storage ,default is ./storage
//                .connectTimeout(0) // set connect timeout
        logd { "edgeTTS request2..." }
        val realFileName = tts.trans()
        logd { "edgeTTS response! $realFileName" }
        // you can find the voice file in storage folder
        playSound(realFileName)
    }

    private fun playSound(filename:String) {
        val file = File(baseDir(), filename).absolutePath
        logd { "edgeTTS play $file" }
        mPlayer?.start(file)
    }

//    fun should_convert_to_mp3_file_success_with_multi_content() {
//        val voiceName = "zh-CN-XiaoyiNeural"
//        val voiceOptional: Optional<Voice> = TTSVoice.provides()
//            .stream()
//            .filter { v: Voice? -> voiceName == v!!.getShortName() }
//            .findFirst()
//        check(voiceOptional.isPresent()) { "voice not found：" + voiceName }
//        val voice: Voice = voiceOptional.get()
//        val recordList: MutableList<TransRecord> = ArrayList<TransRecord>()
//        val store = "./storage"
//
//        // create batch task
//        for (i in 0..99) {
//            val record = TransRecord()
//            record.setContent(i.toString() + ", hello tts, 你好，有什么可以帮助你的吗")
//            record.setFilename(i.toString() + ".test-tts")
//            recordList.add(record)
//            Files.deleteIfExists(Paths.get(buildFilename(store, record)))
//        }
//        TTS(voice)
//            .findHeadHook()
//            .isRateLimited(true)
//            .overwrite(true)
//            .batch(recordList) // set batch task
//            .parallel(12) // set up 12 parallel threads
//            .storage(store)
//            .formatMp3()
//            .batchTrans() // trans
//        for (record in recordList) {
//            val path: Path = Paths.get(buildFilename(store, record))
//            Assertions.assertTrue(Files.exists(path), "file not found in " + path.toString())
//        }
//    }
}