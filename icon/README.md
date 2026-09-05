# 本地课表 (Schedule App) - 极简纯白卡片矢量图标资产包 (1:1 等宽高版)

专为 **Kotlin Android 本地课表应用** 定制的极简矢量图标。

---

## 🎨 视觉设计与比例升级

1. **主体部分严格 1:1 等宽高（Square Card 1:1 Aspect Ratio）**：
   - 将主体纯白课表便签卡片的尺寸调整为严格的 **1:1 长宽等比**（`82dp × 82dp`，平滑圆角 `rx = 19dp`）。
   - 告别原本略显竖长的形态，整体形态更加端庄、方正、居中稳定。
2. **红色书签折角自然置顶（Bookmark Ribbon Accent）**：
   - 右上角的温润暖珊瑚粉书签折角（`#FF8FA3` → `#FF758F`）从主体卡片顶部向上延展悬挂，不破坏卡片本身的 1:1 方正轮廓，极具物理手账辨识度。
3. **更开阔的 3 列排课与时钟元素**：
   - 1:1 比例拓宽了内部排课栏目宽度（每列宽度增至 17dp），课程条与准时时钟微缩标更加舒展呼吸，各尺寸下均具备极高的辨识度。
4. **低对比度马卡龙护眼配色**：
   - 晨间粉杏（早课 1）、清爽薄荷绿（下午课 2）、静谧晴空蓝（周二核心大课 + 活跃标签）、奶油浅杏黄（早课 4）、淡丁香紫罗兰（通识课 5）。
   - 搭配双层细腻环境弥散阴影与 `#E2E8F0` 边缘微描边，在深色、浅色及彩色壁纸下皆轮廓分明。

---

## 📁 资产文件清单

```text
x:/schedule/icon/
├── ic_launcher.svg                 # 矢量主文件 (主体 1:1 纯白卡片 + 柔和配色 + 透明背景)
├── ic_launcher_foreground.svg      # 前景层矢量文件
├── ic_launcher_background.svg      # 背景层矢量文件 (纯净透明)
├── ic_launcher_512.png             # 512×512 高清透明底 PNG (Google Play 商店 / 网页展示)
├── preview_showcase.png            # 深/浅/彩壁纸与全 DPI 尺寸渲染展示图
├── preview.html                    # 本地交互式多尺寸预览页面 (双击直接在浏览器查看)
├── build_icon_assets.py            # 矢量资源全自动构建脚本
└── res/                            # Android Studio 工程就绪目录 (直接拷贝至 app/src/main/res/)
    ├── drawable/
    │   ├── ic_app_icon.xml             # 独立完整 1:1 纯白卡片矢量图标 (推荐直接用于应用图标)
    │   ├── ic_app_logo.xml             # 应用 Logo (可用于关于页/闪屏页)
    │   ├── ic_launcher_foreground.xml  # 自适应前景 VectorDrawable
    │   ├── ic_launcher_background.xml  # 自适应背景 (透明)
    │   └── ic_launcher_monochrome.xml  # Android 13+ Material You 动态取色单色图标
    └── mipmap-anydpi-v26/
        ├── ic_launcher.xml             # 自适应图标清单入口
        └── ic_launcher_round.xml       # 圆形自适应图标清单入口
```

---

## 🚀 Android Kotlin 工程快速集成

### 方式一（推荐·独立矢量图标）
在 `AndroidManifest.xml` 中将应用图标指向 `ic_app_icon`：

```xml
<application
    android:allowBackup="true"
    android:icon="@drawable/ic_app_icon"
    android:roundIcon="@drawable/ic_app_icon"
    android:label="@string/app_name"
    android:theme="@style/Theme.ScheduleApp">
    ...
</application>
```

### 方式二（标准自适应图标）
将 [res/](file:///x:/schedule/icon/res/) 目录直接复制合并到 Android 工程的 `app/src/main/res/`：

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    android:theme="@style/Theme.ScheduleApp">
    ...
</application>
```

### 在 Jetpack Compose 中使用
```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ScheduleAppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_app_icon),
        contentDescription = "本地课表 App Logo",
        modifier = modifier.size(72.dp)
    )
}
```

---

## 📱 效果预览
- 双击直接打开 [preview.html](file:///x:/schedule/icon/preview.html) 可在浏览器中交互查看在深色、浅色、彩色壁纸以及从 144px 到 48px 各种真实分辨率下的渲染效果。
- 也可直接查看全景效果图 [preview_showcase.png](file:///x:/schedule/icon/preview_showcase.png) 或透明底原图 [ic_launcher_512.png](file:///x:/schedule/icon/ic_launcher_512.png)。
