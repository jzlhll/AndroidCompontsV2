package com.au.audiorecordplayer.cam2.view.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import javax.microedition.khronos.opengles.GL10

/**
 * Created by lb6905 on 2017/6/28.
 */
class Utils {
    companion object {
        @JvmStatic
        fun createOESTextureObject(): Int {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE.toFloat()
            )
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            return tex[0]
        }

        /**
         * 标准不做任何修改。
         */
        val BASE_FRAGMENT_ORIGINAL_SHADER: String = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            void main()
            {
              gl_FragColor = texture2D(uTextureSampler, vTextureCoord);
            }
            
            """.trimIndent()

        @JvmField
        val BASE_FRAGMENT_SHADER: String = """
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
        """.trimIndent()

        @JvmField
        val BASE_VERTEX_SHADER: String = """
        attribute vec4 aPosition;
        uniform mat4 uTextureMatrix;
        attribute vec4 aTextureCoordinate;
        varying vec2 vTextureCoord;
        void main()
        {
          vTextureCoord = (uTextureMatrix * aTextureCoordinate).xy;
          gl_Position = aPosition;
        }
        """.trimIndent()
    }
}
