// 通用的使用 oes 纹理的片段着色器

#extension GL_OES_EGL_image_external : require

// 定义浮点精度为 mediump
precision mediump float;

uniform samplerExternalOES uOESTexture;

// 纹理坐标
varying vec2 vTextureXY;

void main() {
    // 设置该纹理坐标点的颜色
    gl_FragColor = texture2D(uOESTexture, vTextureXY);
}