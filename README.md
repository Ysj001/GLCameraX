### GLCameraX

#### 前言

CameraX 是一个 Jetpack 库，旨在帮助您更轻松地开发相机应用。它提供一致且易于使用的 API，适用于绝大多数 Android 设备，并向后兼容 Android 5.0（API 级别 21）。

虽然 CameraX 已经封装了大部分对相机操作，但想要基于 OpenGL 做一个可以自定义渲染流程的相机还是有不少东西需要处理，如 OpenGL 环境的封装，需要自定义的 SurfaceProvider，和图片视频捕获。

本项目在 `lib_camera` 中提供了这一些列封装用来解决这些痛点，你可以添加自定义的相机渲染节点 `CameraRenderer`  来修改渲染流程。



#### 使用



#### 最后

