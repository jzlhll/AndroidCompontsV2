package com.allan.androidlearning.tts_asr;

import android.media.MediaPlayer;
import java.io.IOException;

/**
 * MediaPlayer的状态就比较严谨了，这里只做简单的状态管理。
 * 如果过于随意点击则会报错。
 */
public class MyMediaPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private MediaPlayer mMediaPlayer;
    private St mCurrentSt = St.NOT_INIT;

    private static final int MSG_UPDATE_VISIBLE = 2;
    private static final int MSG_UPDATE_POS = 1;
    private static final int MSG_UPDATE_POS_INIT = 0;

    public enum St {
        NOT_INIT,
        INIT,
        PLAYING,
        PAUSED,
    }

    private StCallback mStCallback;

    public void setStCallback(StCallback s) {
        mStCallback = s;
    }

    public MyMediaPlayer() {
    }

    public void start(String filePath) {
        if (mCurrentSt == St.PAUSED || mCurrentSt == St.PLAYING) {
            return;
        }

        if (St.NOT_INIT == mCurrentSt) {
            mMediaPlayer = new MediaPlayer();
        }

        //1. 第一种方案
        //setDataSource (String path)
        //setDataSource (FileDescriptor fd)
        //setDataSource (Context context, Uri uri)
        //setDataSource (FileDescriptor fd, long offset, long length)
        try {
            mMediaPlayer.setDataSource(filePath);
        } catch (IOException e) {
            throw new RuntimeException("错误的初始化mediaplayer");
        }
        //2. 第二种方案
        //TODO mMediaPlayer = MediaPlayer.create(context, R.raw.soundeffect_paopao);
        //下面的选择可以打开一个
        //mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //TODO mMediaPlayer.setAudioStreamType();
        mCurrentSt = St.INIT;
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.prepareAsync();
    }

    public void pause() {
        if (mCurrentSt == St.PLAYING) {
            mMediaPlayer.pause();
            mCurrentSt = St.PAUSED;
            mStCallback.onStChange(St.PAUSED);
        }
    }

    public void resume() {
        if (mCurrentSt == St.PAUSED) {
            mMediaPlayer.start();
            mCurrentSt = St.PLAYING;
            mStCallback.onStChange(St.PLAYING);
        }
    }

    public void stop() {
        if (mCurrentSt == St.NOT_INIT) {
            return;
        }
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mCurrentSt = St.INIT;
        mStCallback.onStChange(St.INIT);
    }

    public void release() {
        mMediaPlayer.release();
        mCurrentSt = St.NOT_INIT;
        mStCallback.onStChange(St.NOT_INIT);
        mMediaPlayer = null;
    }

    //setOnPreparedListener(this)实现的方法
    @Override
    public void onPrepared(MediaPlayer mp) {
        mMediaPlayer.start();
        mCurrentSt = St.PLAYING;
        mStCallback.onStChange(St.PLAYING);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stop();
    }

    public interface StCallback {
        void onStChange(St st);
    }
}
