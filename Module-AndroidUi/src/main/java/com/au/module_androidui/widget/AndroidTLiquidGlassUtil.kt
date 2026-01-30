package com.au.module_androidui.widget

import org.intellij.lang.annotations.Language

class AndroidTLiquidGlassUtil {
    @Language("AGSL")
    val liquidGlassView1 = """
uniform float3 iResolution; 
uniform float  iTime; 
uniform float4 iMouse; 
uniform float3 iImageResolution; 
uniform shader iImage1;
uniform float  useCircle; float2 R;
const float PI = 3.14159265;
 
// 创建旋转矩阵
float2x2 Rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return float2x2(c, -s, s, c);
}
 
// 像素归一化处理
float PX(float a) {
    return a / R.y;
}
 
// 矩形距离场
float Box(float2 p, float2 b) {
    float2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}
 
// 圆形距离场
float Circle(float2 p, float r) {
    return length(p) - r;
}
 
// 根据 useCircle 选择形状（圆形或圆角矩形）
float Shape(float2 p, float2 b, float r) {
    return useCircle > 0.5 ? Circle(p, r) : Box(p, b);
}
 
// 液体玻璃效果
float4 LiquidGlass(float2 uv, float direction, float quality, float size) {
    float2 radius = size / R;
    float4 color = iImage1.eval(uv * iImageResolution.xy);
 
    float d_step = PI / direction; // 方向步长，direction = 10.0
    float i_step = 1.0 / quality;  // 质量步长，quality = 10.0
    float d = 0.0;
    for (int j = 0; j < 10; j++) { // 固定10次迭代（direction = 10.0）
        float i = i_step;
        for (int k = 0; k < 10; k++) { // 固定10次迭代（quality = 10.0）
            color += iImage1.eval((uv + float2(cos(d), sin(d)) * radius * i) * iImageResolution.xy);
            i += i_step;
        }
        d += d_step;
    }
 
    color /= quality * direction; // 归一化，10.0 * 10.0 = 100.0
    return color;
}
 
// 形状扭曲效果
float4 Distortion(float2 uv) {
    float shape = Shape(uv, float2(PX(50.0)), PX(50.0));
    float shapeShape = smoothstep(PX(1.5), 0.0, shape - PX(50.0)); // 形状平滑过渡
    float shapeDisp = smoothstep(PX(75.0), 0.0, shape - PX(25.0)); // 边框宽度
    float shapeLight = shapeShape * smoothstep(0.0, PX(20.0), shape - PX(40.0)); // 光照强度
    return float4(shapeShape, shapeDisp, shapeLight, 0.0);
}
 
float4 main(float2 I) {
    R = iResolution.xy;
    float2 uv = I / R; // 归一化UV坐标
    float2 st = (I - 0.5 * R) / R.y; // 屏幕空间坐标
    float2 M = iMouse.xy == float2(0.0) ? float2(0.0) : (iMouse.xy - 0.5 * R) / R.y; // 鼠标位置
 
    float4 dist = Distortion(st - M); // 计算扭曲效果
 
    float2 uv2 = uv - iMouse.xy / R; // 为了适配shaders.skia.org的调试
    uv2 *= 0.5 + 0.5 * smoothstep(0.5, 1.0, dist.y); // 缩放UV
    uv2 += iMouse.xy / R;
 
    float3 col = mix(float3(0.0), // 透明黑色背景
                     0.2 + LiquidGlass(uv2, 10.0, 10.0, 5.0).rgb * 0.7, // 应用液体玻璃效果
                     dist.x); // 根据图标形状混合
    col += dist.z * 0.9 + dist.w; // 添加图标光照和图案
 
    // 使用 dist.x 控制透明度：图标区域不透明，其他区域透明
    float alpha = dist.x > 0.0 ? 1.0 : 0.0;
 
    // 应用阴影效果，保持透明度
    col *= 1.0 - 0.2 * smoothstep(PX(80.0), 0.0, Shape(st - M + float2(0.0, PX(40.0)), float2(PX(50.0)), PX(50.0)));
 
    return float4(col, alpha); // 返回最终颜色和透明度
}
    """.trimIndent()
}