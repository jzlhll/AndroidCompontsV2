package com.au.audiorecordplayer.cam2.view.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by lb6905 on 2017/6/12.
 */
class FilterEngine(val oESTextureId: Int,
                   initialFilterType: FilterType = FilterType.ORIGINAL) {
    companion object {
        private val vertexData = floatArrayOf(
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f,
            -1f, -1f, 0f, 0f,
            1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f
        )

        const val POSITION_ATTRIBUTE: String = "aPosition"
        const val TEXTURE_COORD_ATTRIBUTE: String = "aTextureCoordinate"
        const val TEXTURE_MATRIX_UNIFORM: String = "uTextureMatrix"
        const val TEXTURE_SAMPLER_UNIFORM: String = "uTextureSampler"
    }

    val buffer: FloatBuffer = createBuffer(vertexData)

    var shaderProgram: Int = -1
        private set
    
    // 当前使用的滤镜类型
    var currentFilterType: FilterType = initialFilterType
        private set

    init {
        // 使用指定的滤镜类型初始化
        updateFilter(currentFilterType)
    }
    
    /**
     * 更新当前使用的滤镜
     * @param filterType 要切换到的滤镜类型
     */
    fun updateFilter(filterType: FilterType) {
        // 保存新的滤镜类型
        currentFilterType = filterType
        
        // 如果之前已经有着色器程序，需要先释放
        if (shaderProgram != -1) {
            GLES20.glDeleteProgram(shaderProgram)
        }
        
        // 加载顶点着色器
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, GLConsts.BASE_VERTEX_SHADER)
        // 根据滤镜类型加载对应的片段着色器
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, GLConsts.getFragmentShaderByType(filterType))
        // 链接并使用新的着色器程序
        shaderProgram = linkProgram(vertexShader, fragmentShader)
    }

    fun createBuffer(vertexData: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertexData, 0, vertexData.size).position(0)
        return buffer
    }

    fun loadShader(type: Int, shaderSource: String?): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Create Shader Failed!" + GLES20.glGetError())
        }
        GLES20.glShaderSource(shader, shaderSource)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun linkProgram(verShader: Int, fragShader: Int): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Create Program Failed!" + GLES20.glGetError())
        }
        GLES20.glAttachShader(program, verShader)
        GLES20.glAttachShader(program, fragShader)
        GLES20.glLinkProgram(program)

        GLES20.glUseProgram(program)
        return program
    }

    fun drawTexture(transformMatrix: FloatArray?) {
        val aPositionLocation = GLES20.glGetAttribLocation(this.shaderProgram, POSITION_ATTRIBUTE)
        val aTextureCoordLocation = GLES20.glGetAttribLocation(this.shaderProgram, TEXTURE_COORD_ATTRIBUTE)
        val uTextureMatrixLocation = GLES20.glGetUniformLocation(this.shaderProgram, TEXTURE_MATRIX_UNIFORM)
        val uTextureSamplerLocation = GLES20.glGetUniformLocation(this.shaderProgram, TEXTURE_SAMPLER_UNIFORM)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this.oESTextureId)
        GLES20.glUniform1i(uTextureSamplerLocation, 0)
        GLES20.glUniformMatrix4fv(uTextureMatrixLocation, 1, false, transformMatrix, 0)

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 16, this.buffer)

        buffer.position(2)
        GLES20.glEnableVertexAttribArray(aTextureCoordLocation)
        GLES20.glVertexAttribPointer(aTextureCoordLocation, 2, GLES20.GL_FLOAT, false, 16, this.buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

}

