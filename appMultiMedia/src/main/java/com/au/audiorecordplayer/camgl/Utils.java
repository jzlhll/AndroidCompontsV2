package com.au.audiorecordplayer.camgl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by lb6905 on 2017/6/28.
 */

public class Utils {

    public static int createOESTextureObject() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return tex[0];
    }

    public static final String BASE_FRAGMENT_SHADER =
        """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTextureSampler;
        varying vec2 vTextureCoord;
        void main()
        {
          vec4 vCameraColor = texture2D(uTextureSampler, vTextureCoord);
          float fGrayColor = (0.3*vCameraColor.r + 0.59*vCameraColor.g + 0.11*vCameraColor.b);
          gl_FragColor = vec4(fGrayColor, fGrayColor, fGrayColor, 1.0);
        }
        """;

    public static final String BASE_VERTEX_SHADER =
        """
        attribute vec4 aPosition;
        uniform mat4 uTextureMatrix;
        attribute vec4 aTextureCoordinate;
        varying vec2 vTextureCoord;
        void main()
        {
          vTextureCoord = (uTextureMatrix * aTextureCoordinate).xy;
          gl_Position = aPosition;
        }
        """;
}
