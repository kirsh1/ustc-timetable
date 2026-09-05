# USTC Timetable

一个为解决学校 Web 课表在手机上使用不便而制作的个人 Android 小工具。它通过学校统一身份认证导入课表，在本地提供按周浏览、手动事项、课程详情、学期管理与静默同步。

> [!IMPORTANT]
> 本项目由个人开发，与中国科学技术大学及其教务部门无隶属、授权或背书关系。学校门户结构发生变化时，导入与同步功能可能暂时不可用。

## 功能

- 通过校内统一身份认证导入当前学期课表
- 自动定位当前选课轮次，并支持登录后自动完成导入
- 周课表、周概览、学期切换与非本周课程提示
- 学校课程详情及同一时段冲突内容的横向切换
- 创建、查看、编辑和删除本地手动事项
- 每周静默同步、手动同步及登录失效后的重新认证
- 浅色、深色与跟随系统主题
- 自定义课程色系、课表壁纸及壁纸显示强度
- 学期绑定作息，保留历史学期的时间解释

## 隐私与数据安全

- App 不保存学校账号或密码；登录由学校认证页面完成。
- 登录会话仅用于访问学校教务页面，并使用 Android Keystore 支持的 AES-GCM 加密后保存在设备本地。
- 课表和手动事项保存在设备本地，不上传到本项目维护者的服务器。
- 学校同步失败时保留已有本地课表；同步不会覆盖手动事项。
- 应用已禁用 Android 系统备份，避免会话随备份迁移。

使用本项目前，请自行确认学校相关系统的使用规则，并妥善保护个人信息。

## 技术栈

- Kotlin 2.4 / Java 17
- Jetpack Compose + Material 3
- Room、DataStore、WorkManager
- OkHttp、Jsoup、Kotlin Serialization
- Android Gradle Plugin 9.3
- minSdk 26，targetSdk 36，compileSdk 37

## 构建

需要 JDK 17 与 Android SDK。仓库包含 Gradle Wrapper。

```powershell
.\gradlew.bat :app:assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

执行 JVM 单元测试与 lint：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

在已连接的 Android 设备或模拟器上执行仪器测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=1
```

当前 `release` 构建仍使用调试签名，仅用于开发验收；正式分发前应配置独立发布签名并妥善保管密钥。

## 项目结构

```text
app/src/main/java/com/ustc/timetable/
├── appearance/       主题与壁纸
├── manual/           手动事项
├── scheduleprofile/  作息配置
├── school/ustc/      登录、会话、门户与解析器
├── semester/         学期导入与管理
├── settings/         设置
├── sync/             同步与后台任务
└── timetable/        课表领域、数据、布局与界面
```

`docs/superpowers/` 保存设计规格、实施计划及资格验证记录；`icon/` 保存应用图标的 SVG、预览与生成工具。

## 许可与广告立场

源码采用 [GNU Affero General Public License v3.0 only](LICENSE) 许可。

作者不会在本项目中加入：

- 不加入遮挡或持续干扰内容的“牛皮癣”应用内广告；
- 不加入启动、开屏或启动流程插屏广告。

我也希望使用或修改这份源码的人尊重这一选择，不要把它改造成依靠上述广告获利的应用。这是一项非约束性的使用倡议，不是对 AGPL 所授予权利的附加限制；标准 AGPL 仍允许商业使用、修改与再分发。

Copyright © 2026 kirsh1
