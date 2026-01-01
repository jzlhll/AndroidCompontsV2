package com.au.audiorecordplayer.cam2.view.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.module_android.log.logdNoFile
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
    private val mFBOIds = IntArray(1)

    // FilterEngine引用，用于动态切换滤镜
    private var mFilterEngine: FilterEngine? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mOESTextureId = createOESTextureObject()
        // 创建FilterEngine并保存引用
        val filterEngine = FilterEngine(mOESTextureId, initialFilterType = DataRepository.shaderModeFlow.value)
        mFilterEngine = filterEngine
        val st = SurfaceTexture(mOESTextureId)
        mSurfaceTexture = st
        st.setOnFrameAvailableListener { _: SurfaceTexture? ->
            mGLView.requestRender()
        }
        
        mDataBuffer = filterEngine.buffer
        GLES20.glGenFramebuffers(1, mFBOIds, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBOIds[0])
        Log.i(TAG, "onSurfaceCreated: mFBOId: " + mFBOIds[0])
        
        mGLView.getCallback()?.onSurfaceCreated(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        // 保存宽高信息，用于后续滤镜切换
        DataRepository.currentWidth = width
        DataRepository.currentHeight = height
        mGLView.getCallback()?.onSurfaceChanged()
        
        // 如果当前正在使用需要尺寸参数的滤镜，重新应用滤镜以更新参数
        mFilterEngine?.let { filterEngine->
            if (filterEngine.currentFilterType.needSize()) {
                filterEngine.updateFilter(filterEngine.currentFilterType)
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val t1 = System.currentTimeMillis()
        mSurfaceTexture?.let { st->
            st.updateTexImage()
            st.getTransformMatrix(transformMatrix)
        }

        //glClear(GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f)

        val sp = mFilterEngine?.shaderProgram ?: -1
        val aPositionLocation = GLES20.glGetAttribLocation(sp, FilterEngine.POSITION_ATTRIBUTE)
        val aTextureCoordLocation = GLES20.glGetAttribLocation(sp, FilterEngine.TEXTURE_COORD_ATTRIBUTE)
        val uTextureMatrixLocation = GLES20.glGetUniformLocation(sp, FilterEngine.TEXTURE_MATRIX_UNIFORM)
        val uTextureSamplerLocation = GLES20.glGetUniformLocation(sp, FilterEngine.TEXTURE_SAMPLER_UNIFORM)

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
        logdNoFile {
            "onDrawFrame: time: $t"
        }
    }

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
     * 切换滤镜效果
     * @param filterType 滤镜类型
     */
    fun changeFilter(filterType: FilterType) {
        val fe = mFilterEngine ?: return
        // 确保在GL线程中执行
        mGLView.queueEvent {
            fe.updateFilter(filterType)
            Log.d(TAG, "Filter changed to: $filterType")
        }
    }
}
