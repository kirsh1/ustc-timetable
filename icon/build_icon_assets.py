import os
import subprocess

base_dir = os.path.dirname(os.path.abspath(__file__))
chrome_path = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

def rrect_path(x, y, w, h, r):
    return (
        f"M{x+r:.2f},{y:.2f} "
        f"h{w-2*r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{r:.2f} "
        f"v{h-2*r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{r:.2f} "
        f"h{-w+2*r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{-r:.2f} "
        f"v{-h+2*r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{-r:.2f} z"
    )

def circle_path(cx, cy, r):
    return f"M{cx-r:.2f},{cy:.2f} a{r:.2f},{r:.2f} 0 1 0 {2*r:.2f},0 a{r:.2f},{r:.2f} 0 1 0 {-2*r:.2f},0 z"

# 1. Master SVG: 1:1 Aspect Ratio (82 x 82 main card body)
def get_master_svg(size=512):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">
  <defs>
    <!-- Soft Harmonious Pastel Gradients (Low contrast, eye-friendly) -->
    <!-- Ribbon: Soft Warm Rosy Coral -->
    <linearGradient id="ribbonGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FF8FA3"/>
      <stop offset="100%" stop-color="#FF758F"/>
    </linearGradient>

    <!-- Monday Morning: Soft Peach Rose -->
    <linearGradient id="c1Grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFAAA6"/>
      <stop offset="100%" stop-color="#FF928B"/>
    </linearGradient>

    <!-- Monday Afternoon: Soft Mint Green -->
    <linearGradient id="c2Grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#98E4B7"/>
      <stop offset="100%" stop-color="#76D7A0"/>
    </linearGradient>

    <!-- Tuesday Core Class: Soft Serenity Sky Blue -->
    <linearGradient id="c3Grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#93C5FD"/>
      <stop offset="100%" stop-color="#60A5FA"/>
    </linearGradient>

    <!-- Wednesday Morning: Soft Butter Yellow -->
    <linearGradient id="c4Grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FDE68A"/>
      <stop offset="100%" stop-color="#FCD34D"/>
    </linearGradient>

    <!-- Wednesday Afternoon: Soft Lavender Iris -->
    <linearGradient id="c5Grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#D8B4FE"/>
      <stop offset="100%" stop-color="#C084FC"/>
    </linearGradient>

    <!-- Natural Soft Elevation Drop Shadow for Card -->
    <filter id="cardShadow" x="-15%" y="-15%" width="130%" height="140%">
      <feDropShadow dx="0" dy="3.5" stdDeviation="3.5" flood-color="#0F172A" flood-opacity="0.09"/>
      <feDropShadow dx="0" dy="1" stdDeviation="1.5" flood-color="#0F172A" flood-opacity="0.05"/>
    </filter>
  </defs>

  <!-- The White Schedule Card (1:1 Ratio: 82x82, rounded corners) -->
  <g id="schedule_card" filter="url(#cardShadow)">
    <!-- Main Card Body: Exactly 82 x 82 (Aspect Ratio 1:1) -->
    <rect x="13" y="15" width="82" height="82" rx="19" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="1.2"/>

    <!-- Bookmark Ribbon on Top-Right (Hanging over top edge) -->
    <path d="M 70,11 L 79,11 L 79,33.5 L 74.5,29.5 L 70,33.5 Z" fill="url(#ribbonGrad)"/>

    <!-- Header Day Indicators -->
    <rect x="22.5" y="24" width="11" height="4.5" rx="2.25" fill="#E2E8F0"/>
    <!-- Tuesday Active Pill -->
    <rect x="39" y="23" width="20" height="6.5" rx="3.25" fill="#60A5FA"/>
    <!-- Wednesday Dot (Placed cleanly to the left of ribbon) -->
    <circle cx="64.5" cy="26.25" r="2.25" fill="#E2E8F0"/>

    <!-- Header Divider Line -->
    <line x1="20" y1="33.5" x2="88" y2="33.5" stroke="#F1F5F9" stroke-width="1.2"/>

    <!-- 3 Course Columns -->
    <!-- Column 1: Monday -->
    <g id="col_monday">
      <rect x="22.5" y="39" width="17" height="24.5" rx="5" fill="url(#c1Grad)"/>
      <line x1="26.5" y1="46" x2="35.5" y2="46" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.95"/>
      <line x1="26.5" y1="51.5" x2="32.5" y2="51.5" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.8"/>

      <rect x="22.5" y="68.5" width="17" height="18" rx="5" fill="url(#c2Grad)"/>
      <line x1="26.5" y1="77.5" x2="35.5" y2="77.5" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.95"/>
    </g>

    <!-- Column 2: Tuesday (Today - Active Core Class) -->
    <g id="col_tuesday">
      <rect x="44.5" y="39" width="17" height="37.5" rx="5" fill="url(#c3Grad)"/>
      <!-- Punctuality Clock Accent Glyph -->
      <circle cx="53" cy="48" r="4.8" fill="none" stroke="#FFFFFF" stroke-width="1.5" opacity="0.95"/>
      <polyline points="53,45.2 53,48 55.5,48" fill="none" stroke="#FFFFFF" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.95"/>
      <line x1="48.5" y1="58" x2="57.5" y2="58" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.9"/>
      <line x1="48.5" y1="64.5" x2="54.5" y2="64.5" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.75"/>
    </g>

    <!-- Column 3: Wednesday -->
    <g id="col_wednesday">
      <rect x="66.5" y="39" width="17" height="19.5" rx="5" fill="url(#c4Grad)"/>
      <line x1="70.5" y1="46" x2="79.5" y2="46" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.95"/>

      <rect x="66.5" y="63.5" width="17" height="23" rx="5" fill="url(#c5Grad)"/>
      <line x1="70.5" y1="71" x2="79.5" y2="71" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.95"/>
      <line x1="70.5" y1="76.5" x2="76" y2="76.5" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" opacity="0.8"/>
    </g>
  </g>
</svg>"""

# 2. Android Vector Drawable: ic_app_icon.xml (1:1 Ratio: 82x82)
def get_vd_app_icon_xml():
    shadow1 = rrect_path(13, 18, 82, 82, 19)
    shadow2 = rrect_path(13, 16.5, 82, 82, 19)
    card = rrect_path(13, 15, 82, 82, 19)
    tab1 = rrect_path(22.5, 24, 11, 4.5, 2.25)
    tab2 = rrect_path(39, 23, 20, 6.5, 3.25)
    tab3 = circle_path(64.5, 26.25, 2.25)
    c1 = rrect_path(22.5, 39, 17, 24.5, 5)
    c2 = rrect_path(22.5, 68.5, 17, 18, 5)
    c3 = rrect_path(44.5, 39, 17, 37.5, 5)
    c4 = rrect_path(66.5, 39, 17, 19.5, 5)
    c5 = rrect_path(66.5, 63.5, 17, 23, 5)

    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Kotlin Android 本地课表应用图标 (纯白卡片·主体1:1等宽高·柔和马卡龙色调) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Card Natural Ambient Shadows (Subtle elevation without blur filter) -->
    <path
        android:pathData="{shadow1}"
        android:fillColor="#0F172A"
        android:fillAlpha="0.07" />
    <path
        android:pathData="{shadow2}"
        android:fillColor="#0F172A"
        android:fillAlpha="0.05" />

    <!-- Schedule Card Main Sheet: Exactly 82 x 82 (Aspect Ratio 1:1) -->
    <path
        android:pathData="{card}"
        android:fillColor="#FFFFFFFF"
        android:strokeColor="#FFE2E8F0"
        android:strokeWidth="1.2" />

    <!-- Bookmark Ribbon on Top-Right (Hanging over top edge) -->
    <path
        android:pathData="M70,11 L79,11 L79,33.5 L74.5,29.5 L70,33.5 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="74.5"
                android:startY="11"
                android:endX="74.5"
                android:endY="33.5">
                <item android:color="#FFFF8FA3" android:offset="0.0" />
                <item android:color="#FFFF758F" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Header Day Tabs -->
    <path
        android:pathData="{tab1}"
        android:fillColor="#FFE2E8F0" />
    <path
        android:pathData="{tab2}"
        android:fillColor="#FF60A5FA" />
    <path
        android:pathData="{tab3}"
        android:fillColor="#FFE2E8F0" />

    <!-- Header Divider Line -->
    <path
        android:pathData="M20,33.5 L88,33.5"
        android:strokeColor="#FFF1F5F9"
        android:strokeWidth="1.2" />

    <!-- Column 1: Monday -->
    <!-- Course 1 (Soft Peach Rose) -->
    <path
        android:pathData="{c1}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="31"
                android:startY="39"
                android:endX="31"
                android:endY="63.5">
                <item android:color="#FFFFAAA6" android:offset="0.0" />
                <item android:color="#FFFF928B" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:pathData="M26.5,46 L35.5,46"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.95" />
    <path
        android:pathData="M26.5,51.5 L32.5,51.5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.8" />

    <!-- Course 2 (Soft Mint Green) -->
    <path
        android:pathData="{c2}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="31"
                android:startY="68.5"
                android:endX="31"
                android:endY="86.5">
                <item android:color="#FF98E4B7" android:offset="0.0" />
                <item android:color="#FF76D7A0" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:pathData="M26.5,77.5 L35.5,77.5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.95" />

    <!-- Column 2: Tuesday (Today - Active Core Class) -->
    <path
        android:pathData="{c3}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="53"
                android:startY="39"
                android:endX="53"
                android:endY="76.5">
                <item android:color="#FF93C5FD" android:offset="0.0" />
                <item android:color="#FF60A5FA" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Clock Accent Glyph -->
    <path
        android:pathData="M48.2,48 a4.8,4.8 0 1,0 9.6,0 a4.8,4.8 0 1,0 -9.6,0"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.5"
        android:strokeAlpha="0.95" />
    <path
        android:pathData="M53,45.2 L53,48 L55.5,48"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:strokeAlpha="0.95" />
    <path
        android:pathData="M48.5,58 L57.5,58"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.9" />
    <path
        android:pathData="M48.5,64.5 L54.5,64.5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.75" />

    <!-- Column 3: Wednesday -->
    <!-- Course 4 (Soft Butter Yellow) -->
    <path
        android:pathData="{c4}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="75"
                android:startY="39"
                android:endX="75"
                android:endY="58.5">
                <item android:color="#FFFDE68A" android:offset="0.0" />
                <item android:color="#FFFCD34D" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:pathData="M70.5,46 L79.5,46"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.95" />

    <!-- Course 5 (Soft Lavender Iris) -->
    <path
        android:pathData="{c5}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="75"
                android:startY="63.5"
                android:endX="75"
                android:endY="86.5">
                <item android:color="#FFD8B4FE" android:offset="0.0" />
                <item android:color="#FFC084FC" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:pathData="M70.5,71 L79.5,71"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.95" />
    <path
        android:pathData="M70.5,76.5 L76,76.5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeAlpha="0.8" />
</vector>
"""

# 3. Android Vector Drawable: ic_launcher_background.xml (Transparent / Clean)
def get_vd_transparent_background_xml():
    return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Transparent Background (No background shape, only card in foreground) -->
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#00000000" />
</vector>
"""

# 4. Android Vector Drawable: ic_launcher_monochrome.xml (Material You 1:1)
def get_vd_monochrome_xml():
    card = rrect_path(13, 15, 82, 82, 19)
    tab1 = rrect_path(22.5, 24, 11, 4.5, 2.25)
    tab2 = rrect_path(41, 23, 24, 6.5, 3.25)
    tab3 = circle_path(73, 26.25, 2.25)
    c1 = rrect_path(22.5, 39, 17, 24.5, 5)
    c2 = rrect_path(22.5, 68.5, 17, 18, 5)
    c3 = rrect_path(44.5, 39, 17, 37.5, 5)
    c4 = rrect_path(66.5, 39, 17, 19.5, 5)
    c5 = rrect_path(66.5, 63.5, 17, 23, 5)

    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Card Base Outline -->
    <path
        android:pathData="{card}"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="0.2"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.5"
        android:strokeAlpha="0.6" />

    <!-- Ribbon -->
    <path
        android:pathData="M70,11 L79,11 L79,33.5 L74.5,29.5 L70,33.5 Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="0.9" />

    <!-- Tabs & Divider -->
    <path android:pathData="{tab1}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.4" />
    <path android:pathData="{tab2}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.85" />
    <path android:pathData="{tab3}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.4" />
    <path android:pathData="M20,33.5 L88,33.5" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.2" android:strokeAlpha="0.3" />

    <!-- Course Blocks -->
    <path android:pathData="{c1}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.8" />
    <path android:pathData="{c2}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.65" />
    <path android:pathData="{c3}" android:fillColor="#FFFFFFFF" android:fillAlpha="1.0" />
    <path android:pathData="{c4}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.75" />
    <path android:pathData="{c5}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.7" />

    <!-- Clock Outline on Block 3 -->
    <path
        android:pathData="M48.2,48 a4.8,4.8 0 1,0 9.6,0 a4.8,4.8 0 1,0 -9.6,0"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.4"
        android:strokeAlpha="0.5" />
    <path
        android:pathData="M53,45.2 L53,48 L55.5,48"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.4"
        android:strokeLineCap="round"
        android:strokeAlpha="0.5" />
</vector>
"""

def main():
    # 1. Output Master SVGs
    svg_content = get_master_svg(512)
    with open(os.path.join(base_dir, "ic_launcher.svg"), "w", encoding="utf-8") as f:
        f.write(svg_content)
    print("[OK] Updated ic_launcher.svg (1:1 Aspect Ratio)")

    with open(os.path.join(base_dir, "ic_launcher_foreground.svg"), "w", encoding="utf-8") as f:
        f.write(svg_content)
    print("[OK] Updated ic_launcher_foreground.svg")

    with open(os.path.join(base_dir, "ic_launcher_background.svg"), "w", encoding="utf-8") as f:
        f.write("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <!-- Transparent Background -->
</svg>""")
    print("[OK] Updated ic_launcher_background.svg")

    # 2. Output Android res directory
    drawable_dir = os.path.join(base_dir, "res", "drawable")
    mipmap_dir = os.path.join(base_dir, "res", "mipmap-anydpi-v26")
    os.makedirs(drawable_dir, exist_ok=True)
    os.makedirs(mipmap_dir, exist_ok=True)

    vd_app_icon = get_vd_app_icon_xml()
    with open(os.path.join(drawable_dir, "ic_app_icon.xml"), "w", encoding="utf-8") as f:
        f.write(vd_app_icon)
    print("[OK] Created res/drawable/ic_app_icon.xml")

    with open(os.path.join(drawable_dir, "ic_app_logo.xml"), "w", encoding="utf-8") as f:
        f.write(vd_app_icon)
    print("[OK] Updated res/drawable/ic_app_logo.xml")

    with open(os.path.join(drawable_dir, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(vd_app_icon)
    print("[OK] Updated res/drawable/ic_launcher_foreground.xml")

    with open(os.path.join(drawable_dir, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(get_vd_transparent_background_xml())
    print("[OK] Updated res/drawable/ic_launcher_background.xml")

    with open(os.path.join(drawable_dir, "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
        f.write(get_vd_monochrome_xml())
    print("[OK] Updated res/drawable/ic_launcher_monochrome.xml")

    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    with open(os.path.join(mipmap_dir, "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write(adaptive_xml)
    with open(os.path.join(mipmap_dir, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
        f.write(adaptive_xml)
    print("[OK] Updated res/mipmap-anydpi-v26/ configs")

    # 3. Render 512x512 Transparent PNG
    temp_html_path = os.path.join(base_dir, "temp_512.html")
    with open(temp_html_path, "w", encoding="utf-8") as f:
        f.write(f"""<!DOCTYPE html><html><head><style>
* {{ margin:0; padding:0; box-sizing:border-box; }}
body {{ width:512px; height:512px; background:transparent; overflow:hidden; }}
svg {{ width:512px; height:512px; display:block; }}
</style></head><body>{svg_content}</body></html>""")

    png_512_path = os.path.join(base_dir, "ic_launcher_512.png")
    cmd = [
        chrome_path,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--window-size=512,512",
        f"--screenshot={png_512_path}",
        "file:///" + temp_html_path.replace("\\", "/")
    ]
    subprocess.run(cmd)
    if os.path.exists(temp_html_path):
        os.remove(temp_html_path)
    print("[OK] Rendered ic_launcher_512.png (1:1 Transparent background)")

    # 4. Update preview_showcase.png and preview.html
    update_preview_and_showcase(svg_content)

def update_preview_and_showcase(svg_content):
    preview_html_path = os.path.join(base_dir, "preview.html")
    showcase_html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>Kotlin Android 本地课表 App Icon - 主体 1:1 等宽高纯白卡片</title>
  <style>
    :root {{
      --bg-dark: #0F172A;
      --card-dark: #1E293B;
      --accent: #60A5FA;
      --text: #F8FAFC;
      --text-muted: #94A3B8;
      --border: #334155;
    }}
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background-color: var(--bg-dark);
      color: var(--text);
      line-height: 1.6;
      padding: 40px 20px;
    }}
    .container {{ max-width: 1050px; margin: 0 auto; }}
    header {{ text-align: center; margin-bottom: 40px; }}
    header h1 {{
      font-size: 2.1rem;
      font-weight: 700;
      background: linear-gradient(135deg, #93C5FD, #C084FC);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 8px;
    }}
    header p {{ font-size: 1.05rem; color: var(--text-muted); }}
    .badge {{
      display: inline-block;
      background: rgba(96, 165, 250, 0.15);
      color: #93C5FD;
      border: 1px solid rgba(96, 165, 250, 0.35);
      padding: 4px 14px;
      border-radius: 9999px;
      font-size: 0.85rem;
      font-weight: 600;
      margin-top: 10px;
    }}
    .section {{
      background: var(--card-dark);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 28px;
      margin-bottom: 32px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
    }}
    .section h2 {{
      font-size: 1.35rem;
      margin-bottom: 18px;
      display: flex;
      align-items: center;
      gap: 10px;
      color: #F1F5F9;
    }}
    .wallpapers {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }}
    .phone-card {{
      height: 320px;
      border-radius: 28px;
      padding: 24px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      box-shadow: 0 16px 36px rgba(0,0,0,0.4);
    }}
    .wp-dark {{ background: linear-gradient(180deg, #1E293B, #0F172A); border: 1px solid #334155; }}
    .wp-light {{ background: linear-gradient(180deg, #F8FAFC, #E2E8F0); border: 1px solid #CBD5E1; color: #1E293B; }}
    .wp-photo {{ background: linear-gradient(135deg, #667EEA, #764BA2); border: 1px solid rgba(255,255,255,0.2); }}
    .icon-holder {{ width: 140px; height: 140px; margin-bottom: 16px; }}
    .icon-holder svg {{ width: 100%; height: 100%; display: block; }}
    .app-name {{ font-size: 15px; font-weight: 500; letter-spacing: 0.5px; }}

    /* Multi-size row */
    .sizes-row {{
      display: flex;
      align-items: flex-end;
      justify-content: center;
      gap: 32px;
      flex-wrap: wrap;
      padding: 24px;
      background: #0B0F19;
      border-radius: 16px;
      margin-top: 16px;
    }}
    .size-item {{ display: flex; flex-direction: column; align-items: center; gap: 8px; }}
    .size-item svg {{ width: 100% !important; height: 100% !important; display: block; }}
    .size-item span {{ font-size: 0.75rem; color: var(--text-muted); font-weight: 500; }}

    .palette-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-top: 16px; }}
    .color-chip {{
      background: #0B0F19;
      padding: 16px;
      border-radius: 12px;
      border-left: 5px solid;
    }}
    .color-chip h4 {{ font-size: 0.95rem; margin-bottom: 4px; }}
    .color-chip p {{ font-size: 0.8rem; color: var(--text-muted); }}

    pre {{
      background: #0B0F19;
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 16px;
      overflow-x: auto;
      font-family: Consolas, Monaco, monospace;
      font-size: 0.85rem;
      color: #E2E8F0;
      line-height: 1.5;
    }}
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1>Kotlin Android 本地课表 App Icon</h1>
      <p>主体 1:1 等宽高 (82 × 82) • 红色书签自然置顶 • 柔和马卡龙低对比度配色</p>
      <span class="badge">1:1 Square Card & Soft Pastel Edition</span>
    </header>

    <div class="section">
      <h2>📱 各类手机壁纸下的真实呈现效果 (1:1 方正视觉更平衡)</h2>
      <div class="wallpapers">
        <div class="phone-card wp-dark">
          <div class="icon-holder">{svg_content}</div>
          <span class="app-name">本地课表</span>
        </div>
        <div class="phone-card wp-light">
          <div class="icon-holder">{svg_content}</div>
          <span class="app-name" style="color: #0F172A;">本地课表</span>
        </div>
        <div class="phone-card wp-photo">
          <div class="icon-holder">{svg_content}</div>
          <span class="app-name">本地课表</span>
        </div>
      </div>

      <h3 style="font-size: 1.05rem; margin: 24px 0 12px; color: #CBD5E1;">📐 标准分辨率尺寸对照 (从大尺寸到小桌面图标)</h3>
      <div class="sizes-row">
        <div class="size-item">
          <div style="width: 144px; height: 144px;">{svg_content}</div>
          <span>xxhdpi (144×144)</span>
        </div>
        <div class="size-item">
          <div style="width: 96px; height: 96px;">{svg_content}</div>
          <span>xhdpi (96×96)</span>
        </div>
        <div class="size-item">
          <div style="width: 72px; height: 72px;">{svg_content}</div>
          <span>hdpi (72×72)</span>
        </div>
        <div class="size-item">
          <div style="width: 48px; height: 48px;">{svg_content}</div>
          <span>mdpi (48×48 / 桌面标准小图标)</span>
        </div>
      </div>
    </div>

    <div class="section">
      <h2>🎨 协调降噪后的整体柔和色调方案 (马卡龙粉彩)</h2>
      <div class="palette-grid">
        <div class="color-chip" style="border-left-color: #FF8FA3;">
          <h4 style="color: #FF8FA3;">柔暖珊瑚折角 (Ribbon)</h4>
          <p>#FF8FA3 → #FF758F：温润的暖粉珊瑚折角，自然从主体卡片顶部延展。</p>
        </div>
        <div class="color-chip" style="border-left-color: #FFAAA6;">
          <h4 style="color: #FFAAA6;">早课 1：粉杏蜜桃 (Peach Rose)</h4>
          <p>#FFAAA6 → #FF928B：低饱和晨间课程，亲和温和。</p>
        </div>
        <div class="color-chip" style="border-left-color: #98E4B7;">
          <h4 style="color: #98E4B7;">下午课 2：清爽薄荷 (Mint Green)</h4>
          <p>#98E4B7 → #76D7A0：如抹茶般清新柔和，象征理科实验或实操课程。</p>
        </div>
        <div class="color-chip" style="border-left-color: #60A5FA;">
          <h4 style="color: #60A5FA;">主修课 3：静谧晴空蓝 (Serenity Blue)</h4>
          <p>#93C5FD → #60A5FA：轻盈明朗的柔蓝配白时钟，象征专注学习。</p>
        </div>
        <div class="color-chip" style="border-left-color: #FDE68A;">
          <h4 style="color: #FDE68A;">晨间课 4：奶油杏黄 (Butter Yellow)</h4>
          <p>#FDE68A → #FCD34D：明度适中柔和的浅黄，无反光刺眼感。</p>
        </div>
        <div class="color-chip" style="border-left-color: #D8B4FE;">
          <h4 style="color: #D8B4FE;">通识课 5：淡丁香紫 (Lavender Iris)</h4>
          <p>#D8B4FE → #C084FC：淡雅温婉的丁香紫罗兰色，和谐收尾。</p>
        </div>
      </div>
    </div>

    <div class="section">
      <h2>🚀 Kotlin Android 工程集成方式</h2>
      <p style="color: var(--text-muted); margin-bottom: 12px; font-size: 0.9rem;">
        方式一（推荐·独立矢量图标）：在 <code>AndroidManifest.xml</code> 中直接引用生成的纯白卡片矢量文件 <code>ic_app_icon</code>：
      </p>
      <pre>&lt;application
    android:allowBackup="true"
    android:icon="@drawable/ic_app_icon"
    android:roundIcon="@drawable/ic_app_icon"
    android:label="@string/app_name"
    android:theme="@style/Theme.ScheduleApp"&gt;
    ...
&lt;/application&gt;</pre>

      <h3 style="font-size: 1.05rem; margin: 20px 0 12px; color: #CBD5E1;">✨ 在 Jetpack Compose 中使用</h3>
      <pre>import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

Image(
    painter = painterResource(id = R.drawable.ic_app_icon),
    contentDescription = "本地课表 App 图标",
    modifier = Modifier.size(72.dp)
)</pre>
    </div>
  </div>
</body>
</html>"""

    with open(preview_html_path, "w", encoding="utf-8") as f:
        f.write(showcase_html)
    print("[OK] Updated preview.html")

    showcase_png_path = os.path.join(base_dir, "preview_showcase.png")
    cmd = [
        chrome_path,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--window-size=1150,900",
        f"--screenshot={showcase_png_path}",
        "file:///" + preview_html_path.replace("\\", "/")
    ]
    subprocess.run(cmd)
    print("[OK] Rendered preview_showcase.png")

if __name__ == "__main__":
    main()
