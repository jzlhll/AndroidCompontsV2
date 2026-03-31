package com.au.audiorecordplayer.recorder;

import android.content.Context;

public interface ISimpleRecord {
    void start(Context context) throws Exception;
    void stop();
    boolean isRecording();

    String getCurrentFilePath();
}