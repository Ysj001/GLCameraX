
// 定义浮点精度为 mediump
precision mediump float;

uniform sampler2D u2DTexture;

// 纹理坐标
varying vec2 vTextureXY;

void main() {
    // 设置该纹理坐标点的颜色
    gl_FragColor = texture2D(u2DTexture, vTextureXY);
}