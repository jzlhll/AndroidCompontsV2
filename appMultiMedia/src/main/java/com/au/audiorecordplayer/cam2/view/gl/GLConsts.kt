package com.au.audiorecordplayer.cam2.view.gl


/**
 * Created by lb6905 on 2017/6/28.
 */
class GLConsts {
    companion object {
        /**
         * 标准不做任何修改。
         */
        @JvmField
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

        /**
         * 灰色滤镜
         */
        @JvmField
        val GRAY_FRAGMENT_SHADER: String = """
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

        /**
         * 反色滤镜, 颜色（1-原值），类似底片效果
         */
        @JvmField
        val INVERT_FRAGMENT_SHADER: String = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            void main()
            {
              vec4 vCameraColor = texture2D(uTextureSampler, vTextureCoord);
              gl_FragColor = vec4(1.0 - vCameraColor.r, 1.0 - vCameraColor.g, 1.0 - vCameraColor.b, 1.0);
            }
        """.trimIndent()

        /**
         * 怀旧滤镜通过特定的颜色矩阵变换，增强红色和黄色调，营造老照片的感觉
         */
        @JvmField
        val SEPIA_FRAGMENT_SHADER: String = """
           #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            void main()
            {
              vec4 vCameraColor = texture2D(uTextureSampler, vTextureCoord);
              // 调整后的怀旧色调，减少绿色占比，增加红色表现力，保留一些蓝色
              float r = 0.45 * vCameraColor.r + 0.35 * vCameraColor.g + 0.20 * vCameraColor.b;
              float g = 0.35 * vCameraColor.r + 0.35 * vCameraColor.g + 0.30 * vCameraColor.b;
              float b = 0.25 * vCameraColor.r + 0.25 * vCameraColor.g + 0.20 * vCameraColor.b;
              // 添加少量原始色彩混合，使滤镜效果更自然
              float blendFactor = 0.2; // 保留20%的原始色彩
              r = mix(r, vCameraColor.r, blendFactor);
              g = mix(g, vCameraColor.g, blendFactor);
              b = mix(b, vCameraColor.b, blendFactor);
              gl_FragColor = vec4(r, g, b, 1.0);
            }
        """.trimIndent()

        /**
         * 高斯模糊 todo error
         */
        @JvmField
        val GAUSSIAN_FRAGMENT_SHADER: String = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            uniform vec2 uTexelSize; // 纹理尺寸的倒数 (1.0/width, 1.0/height)
            void main()
            {
              vec4 sum = vec4(0.0);
              for(int x = -2; x <= 2; x++) {
                for(int y = -2; y <= 2; y++) {
                  sum += texture2D(uTextureSampler, 
                    vTextureCoord + vec2(float(x) * uTexelSize.x, float(y) * uTexelSize.y));
                }
              }
              gl_FragColor = sum / 25.0;
            }
        """.trimIndent()

        /**
         * 锐化滤镜 error
         */
        @JvmField
        val SHARPEN_FRAGMENT_SHADER: String = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            uniform vec2 uTexelSize;
            void main()
            {
              vec4 center = texture2D(uTextureSampler, vTextureCoord);
              vec4 up = texture2D(uTextureSampler, vTextureCoord + vec2(0.0, uTexelSize.y));
              vec4 down = texture2D(uTextureSampler, vTextureCoord - vec2(0.0, uTexelSize.y));
              vec4 left = texture2D(uTextureSampler, vTextureCoord - vec2(uTexelSize.x, 0.0));
              vec4 right = texture2D(uTextureSampler, vTextureCoord + vec2(uTexelSize.x, 0.0));
              gl_FragColor = 5.0 * center - up - down - left - right;
            }
           """.trimIndent()

        /**
         * 曝光度滤镜 todo error
         */
        @JvmField
        val BRIGHTNESS_FRAGMENT_SHADER: String = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTextureCoord;
            uniform float uExposure; // 曝光度参数，建议范围0.5-2.0
            void main()
            {
              vec4 vCameraColor = texture2D(uTextureSampler, vTextureCoord);
              gl_FragColor = vec4(vCameraColor.rgb * uExposure, 1.0);
            }
            """.trimIndent()
        
        // 根据滤镜类型获取对应的着色器代码
        @JvmStatic
        fun getFragmentShaderByType(type: FilterType): String {
            return when (type) {
                FilterType.ORIGINAL -> BASE_FRAGMENT_ORIGINAL_SHADER
                FilterType.GRAY -> GRAY_FRAGMENT_SHADER
                FilterType.INVERT -> INVERT_FRAGMENT_SHADER
                FilterType.SEPIA -> SEPIA_FRAGMENT_SHADER
                FilterType.GAUSSIAN -> GAUSSIAN_FRAGMENT_SHADER
                FilterType.SHARPEN -> SHARPEN_FRAGMENT_SHADER
                FilterType.BRIGHTNESS -> BRIGHTNESS_FRAGMENT_SHADER
            }
        }

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
