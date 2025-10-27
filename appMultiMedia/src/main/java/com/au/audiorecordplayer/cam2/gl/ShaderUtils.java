package com.au.audiorecordplayer.cam2.gl;

import android.opengl.GLES30;

import com.au.audiorecordplayer.util.MyLog;

/**
 * 着色器：Shader
 * 顶点着色器 (Vertex Shader)
 *      负责处理每个顶点的位置和属性数据 它会对每个顶点执行一次，主要功能包括：
 *      坐标空间变换（模型视图变换、投影变换）; 法向量处理 ; 纹理坐标传递
 * <br>
 * 几何着色器 (Geometry Shader) OpenGL ES 3.2+支持
 *      位于顶点和片段着色器之间，能够处理完整的图元并生成新几何体
 *      输入：点、线段、三角形等基本图元
 *      输出：修改后的图元、新增图元或直接丢弃图元
 *      其主要特点是可以访问图元的所有顶点数据，适用于几何细分、动态LOD生成等场景
 *<br>
 * 片段着色器（也称为像素着色器）负责计算每个像素的最终颜色
 * 纹理采样和映射;光照计算;颜色混合和特效处理
 *
 * GLSL(OpenGL Shading Language)编写，在GPU上并行执行，渲染能力。
 */
public class ShaderUtils {
    // 编译着色器
    public static int compileShader(int type, String shaderCode) {
        MyLog.t("compile shader...");
        // 创建着色器对象
        int shader = GLES30.glCreateShader(type);
        if (shader == 0) {
            throw new RuntimeException("创建着色器失败");
        }
        
        // 上传源代码并编译
        GLES30.glShaderSource(shader, shaderCode);
        GLES30.glCompileShader(shader);
        
        // 检查编译状态
        int[] compileStatus = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String infoLog = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new RuntimeException("着色器编译错误: " + infoLog);
        }
        
        return shader;
    }
    
    // 创建着色器程序
    public static int createProgram(String vertexShaderCode, String fragmentShaderCode) {
        // 编译着色器
        int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode);
        
        // 创建程序
        int program = GLES30.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("创建程序失败");
        }
        
        // 附加着色器
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        
        // 链接程序
        GLES30.glLinkProgram(program);
        
        // 检查链接状态
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String infoLog = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new RuntimeException("程序链接错误: " + infoLog);
        }
        
        // 删除着色器对象（已链接到程序中，不再需要）
        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);
        
        return program;
    }
}