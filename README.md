# BiliClean Android

一个实验性质的 Android 客户端 MVP，用来验证“净流式”的 B 站竖屏播放体验：获取推荐视频、过滤明显广告/直播/推广内容、播放公开 `playurl` 返回的视频流，并复刻一部分竖屏播放页、评论区、弹幕和进度条交互。

这个仓库是一个不完善版本，主要用于研究和原型验证，不保证稳定性。

## 当前状态

- Android 原生项目，主要代码是 Java。
- 播放器基于 Media3 / ExoPlayer。
- 支持匿名模式，也支持用户自行登录后保存 Cookie，用于个性化推荐、评论、点赞等接口尝试。
- 支持竖屏播放、上下滑切换、评论抽屉、二级评论抽屉、弹幕、进度条预览图、小电视进度 thumb、点赞/投币/收藏等部分动画。
- UI 仍在高度还原阶段，布局、动效、资源选择都还没有完全稳定。

## 注意

这是非官方项目，不隶属于哔哩哔哩。项目只用于学习、研究和原型验证。

请自行遵守目标站点的服务条款、接口限制和相关法律法规。不要把自己的登录 Cookie、抓包响应、调试截图或私有数据提交到仓库。

## 构建

需要：

- Android Studio 或 JDK 17
- Android SDK
- Gradle，或直接使用仓库中的 Gradle Wrapper

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果本机没有配置 SDK 路径，可以在本地创建 `local.properties`：

```properties
sdk.dir=你的 Android SDK 路径
```

`local.properties` 已被 `.gitignore` 排除。

## 目录

```text
app/src/main/java/com/biliclean/app/   Android 代码
app/src/main/res/                      布局、图标、动画资源
app/build.gradle                       App 构建配置
settings.gradle                        Gradle settings
```

## License

MIT
