package com.au.audiorecordplayer.cam2.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.au.audiorecordplayer.cam2.view.cam.CamGLSurfaceView
import com.au.module_android.utils.logt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer(val glSurfaceView: CamGLSurfaceView) : GLSurfaceView.Renderer {
    private var mProgram = 0
    private var mPositionHandle = 0
    private var mTexCoordHandle = 0
    private var mTextureHandle = 0

    // 顶点坐标 (标准化设备坐标)
    private val vertexCoords = floatArrayOf(
        -1.0f, 1.0f,  // 左上
        -1.0f, -1.0f,  // 左下
        1.0f, 1.0f,  // 右上
        1.0f, -1.0f // 右下
    )

    // 纹理坐标
    private val texCoords = floatArrayOf(
        0.0f, 0.0f,  // 左下
        0.0f, 1.0f,  // 左上  
        1.0f, 0.0f,  // 右下
        1.0f, 1.0f // 右上
    )

    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null

    fun createOrGetTextureId(): Int {
        glSurfaceView.apply {
            logt { "cameraRender created surfaceTexture" }
            GLES30.glGenTextures(1, textureIds, 0)
            val id = textureIds[0]
            if (id == 0) {
                throw RuntimeException("Error: Could not generate a new OpenGL textureId.")
            }
            val st = SurfaceTexture(id)
            setSurfaceTexture(st)
            st.setOnFrameAvailableListener { texture ->
                requestRender()
            }
            return id
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        logt { "cameraRenderer onSurface Created" }
        createOrGetTextureId()

        // 初始化OpenGL ES状态
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 创建着色器程序
        mProgram = ShaderUtils.createProgram(vertexShaderCode, fragmentShaderCode)

        // 获取属性/统一变量的位置
        mPositionHandle = GLES30.glGetAttribLocation(mProgram, "a_Position")
        mTexCoordHandle = GLES30.glGetAttribLocation(mProgram, "a_texCoord")
        mTextureHandle = GLES30.glGetUniformLocation(mProgram, "s_texture")

        // 准备顶点缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexCoords)
        vertexBuffer!!.position(0)


        // 准备纹理坐标缓冲区
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer!!.position(0)

        glSurfaceView.getCallback()?.onSurfaceCreated(glSurfaceView.getSurfaceTextureForce())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        glSurfaceView.getCallback()?.onSurfaceChanged()
    }

    override fun onDrawFrame(gl: GL10?) {
        glSurfaceView.getSurfaceTextureForce().updateTexImage()

        // 清除颜色缓冲区
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // 🎯 关键步骤：使用着色器程序
        GLES30.glUseProgram(mProgram)

        // 启用并设置顶点属性
        GLES30.glEnableVertexAttribArray(mPositionHandle)
        GLES30.glVertexAttribPointer(mPositionHandle, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(mTexCoordHandle)
        GLES30.glVertexAttribPointer(mTexCoordHandle, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)


        // 设置纹理（假设textureId是之前创建的OES纹理）
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, glSurfaceView.textureId)

        if (false) { //为绑定的纹理设置过滤和环绕方式等参数
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }

        GLES30.glUniform1i(mTextureHandle, 0) // 0 对应 GL_TEXTURE0

        // 执行绘制
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)


        // 禁用顶点属性
        GLES30.glDisableVertexAttribArray(mPositionHandle)
        GLES30.glDisableVertexAttribArray(mTexCoordHandle)
    }

    // 顶点着色器代码
    private val vertexShaderCode = """
                #version 300 es
                layout(location = 0) in vec4 a_Position;
                layout(location = 1) in vec2 a_texCoord;
                out vec2 v_texCoord;
                void main() {
                    gl_Position = a_Position;
                    v_texCoord = a_texCoord;
                }
                """.trimIndent()

    // 片段着色器代码
    private val fragmentShaderCode = """
                #version 300 es
                #extension GL_OES_EGL_image_external_essl3 : require
                precision mediump float;
                in vec2 v_texCoord;
                out vec4 outColor;
                uniform samplerExternalOES s_texture;
                void main(){
                    outColor = texture(s_texture, v_texCoord);
                }
                """.trimIndent()
}