// 通用的 2D 顶点着色器

// attribute 属性：顶点着色器专有，用于跟与外部交互
// varying 属性：该属性定义的值在片段着色器可以直接拿到

uniform mat4 uMatrix;

// 顶点坐标
attribute vec4 aPosition;

// 纹理坐标（可理解为某个点的 x,y）
attribute vec2 aTextureXY;

// 需要传给片段着色器的纹理坐标
varying vec2 vTextureXY;

void main() {
    // 将纹理坐标传给片段着色器
    vTextureXY = aTextureXY;
    // 将顶点设置给 OpenGL
    gl_Position = aPosition * uMatrix;
}