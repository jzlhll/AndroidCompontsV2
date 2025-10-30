package com.au.audiorecordplayer.cam2.view.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by lb6905 on 2017/7/19.
 */
class CameraV2Renderer(val mGLView: CamGLSurfaceView) : GLSurfaceView.Renderer {
    private val TAG = "CameraV2Renderer"

    private var mOESTextureId = -1
    private var mSurfaceTexture: SurfaceTexture? = null
    private val transformMatrix = FloatArray(16)
    private var mDataBuffer: FloatBuffer? = null
    private var mShaderProgram = -1
    private val mFBOIds = IntArray(1)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mOESTextureId = Utils.createOESTextureObject()
        val mFilterEngine = FilterEngine(mOESTextureId)
        val st = SurfaceTexture(mOESTextureId)
        mSurfaceTexture = st
        st.setOnFrameAvailableListener { _: SurfaceTexture? ->
            mGLView.requestRender()
        }
        
        mDataBuffer = mFilterEngine.buffer
        mShaderProgram = mFilterEngine.shaderProgram
        GLES20.glGenFramebuffers(1, mFBOIds, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBOIds[0])
        Log.i(TAG, "onSurfaceCreated: mFBOId: " + mFBOIds[0])
        
        mGLView.getCallback()?.onSurfaceCreated(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        mGLView.getCallback()?.onSurfaceChanged()
    }

    override fun onDrawFrame(gl: GL10?) {
        val t1 = System.currentTimeMillis()
        if (mSurfaceTexture != null) {
            mSurfaceTexture!!.updateTexImage()
            mSurfaceTexture!!.getTransformMatrix(transformMatrix)
        }

        //glClear(GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f)

        val aPositionLocation = GLES20.glGetAttribLocation(mShaderProgram, FilterEngine.Companion.POSITION_ATTRIBUTE)
        val aTextureCoordLocation = GLES20.glGetAttribLocation(mShaderProgram, FilterEngine.Companion.TEXTURE_COORD_ATTRIBUTE)
        val uTextureMatrixLocation = GLES20.glGetUniformLocation(mShaderProgram, FilterEngine.Companion.TEXTURE_MATRIX_UNIFORM)
        val uTextureSamplerLocation = GLES20.glGetUniformLocation(mShaderProgram, FilterEngine.Companion.TEXTURE_SAMPLER_UNIFORM)

        GLES20.glActiveTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOESTextureId)
        GLES20.glUniform1i(uTextureSamplerLocation, 0)
        GLES20.glUniformMatrix4fv(uTextureMatrixLocation, 1, false, transformMatrix, 0)

        mDataBuffer?.let { dataBuffer->
            dataBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPositionLocation)
            GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 16, dataBuffer)

            dataBuffer.position(2)
            GLES20.glEnableVertexAttribArray(aTextureCoordLocation)
            GLES20.glVertexAttribPointer(aTextureCoordLocation, 2, GLES20.GL_FLOAT, false, 16, dataBuffer)
        }

        //glDrawElements(GL_TRIANGLE_FAN, 6,GL_UNSIGNED_INT, 0);
        //glDrawArrays(GL_TRIANGLE_FAN, 0 , 6);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        //glDrawArrays(GL_TRIANGLES, 3, 3);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val t2 = System.currentTimeMillis()
        val t = t2 - t1
        Log.i(TAG, "onDrawFrame: time: $t")
    }
}
