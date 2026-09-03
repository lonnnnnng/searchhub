# SearchHub - 多站影视资源聚合搜索

> 一个 Android 应用，把多个影视 / 网盘 / 短剧 / BT 资源站点聚合到一个搜索框下。
> 输入一个关键词，**流式返回**所有站点结果，按站源分 tab 展示，进入详情查看磁力 / 网盘 / 种子资源，一键复制。

## 功能特性

- 🔍 **多站聚合搜索**：跨站并发搜索，自动去重合并
- ⚡ **流式返回**：每站结果一到立即展示，配顶部 `X/Y 站` 进度条，无需等全部完成
- 📑 **按站源分 tab**：`全部(196)` / `BT影视(8)` / `狐狸君(32)` / … 动态追加，支持横向滚动
- 🎬 **详情资源页**：磁力 / 网盘 / 种子 / 迅雷链接，一键复制、浏览器打开
- ⚙️ **站点域名可配置**：设置页可修改 baseUrl、启停站点，网站换域名无需改代码
- 🛡️ **验证码支持**：雪落影视算术验证码手动弹图，答对后免验证 30 分钟
- 🌐 **网络代理可选**：适配 Clash 等 HTTP 代理（127.0.0.1:7890）
- 🎨 **清爽绿白 UI**：参考影视工具常见风格——绿主色(#1E9C5A)强调、白底灰字、圆角胶囊搜索框、文字绿条 tab，深浅色跟随系统
- 📦 **体积小巧**：Release APK 仅 ~1.8MB（R8 裁剪）

## 已接入站点（13 个）

| 站点 | 搜索方式 | 资源类型 |
|---|---|---|
| BT影视 btbtlb.com | GET /search/ | 磁力 / 种子 |
| 狐狸君 foxjun.com | JSON API | 磁力 / 百度 / 夸克 / 迅雷网盘 |
| 雪落影视 xlys02.com | GET /search/ (算术验证码) | 磁力 / 电驴 / 网盘 |
| SeedHub seedhub.cc | GET /s/ (被 CF 拦截) | 磁力 / 网盘 |
| 云集 binhd.com | GET /resources/?q= | 网盘（POST 跳转） |
| 6v520 6v520.com | 帝国CMS POST | 磁力 / 迅雷 / 电驴 / 网盘 |
| 电影港 dygang.tv | 帝国CMS POST | 磁力 / 迅雷 / 网盘 |
| 电影天堂 dytt8899.com | 帝国CMS POST | 磁力 / 迅雷 / 网盘 |
| 451024 video.451024.xyz | JSON API | 夸克 / 百度 / 迅雷网盘直链 |
| 短剧狗 duanjugou.top | GET /search.php (短剧) | 网盘直链 |
| Showpaw showpaw.xyz | JSON API | 多网盘直链 + 提取码 |
| 比特大雄 btdx8.net | GET /?s= | 磁力 |
| 新版6v xb6v.com | 帝国CMS POST | 磁力 / 迅雷 / 电驴 / 网盘 |

> 搜索 "batman" 实测聚合 196 条结果（含雪落影视 4 条）。

## 快速开始

### 环境要求

- macOS / Linux
- JDK 17+
- Android SDK（compileSdk 36，minSdk 26）
- 真机或模拟器（已开启 USB 调试）

### 构建与安装

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.searchhub.app/.MainActivity
```

### 构建 Release

```bash
cd android
./gradlew :app:assembleRelease
# APK 位于 app/build/outputs/apk/release/app-release.apk
```

> Release 已开启 R8 裁剪（`isMinifyEnabled=true`），APK 约 1.8MB；使用专用 release keystore 签名。

### 版本发布记录

| 版本 | 内容 |
|---|---|
| v1.0.0 | 首个发布：13 站聚合、按站 tab、详情资源 |
| v1.0.1 | 体积优化：开启 R8、移除死依赖，13MB→1.8MB |
| v1.1.0 | 恢复雪落影视验证码流程 |
| v1.2.0 | 搜索结果流式返回，各站完成即时展示 |
| v1.3.0 | UI/UX 全面改版：品牌化首页(热门关键词)、自定义主题(深浅色)、优化搜索/详情/设置页 |
| v1.4.0 | UI 风格重构：参考"追剧"清爽绿白风（绿主色/白底/圆角胶囊/绿条tab） |

## 使用指引

1. 打开 App，顶部输入电影 / 剧集 / 短剧关键词（支持中英文）
2. 点搜索，**结果随各站返回顺序流式出现**，顶部进度条显示 `X/Y 站`，全部完成后自动切为最终结果
3. 点具体站 tab 查看该站全部结果；点"全部"查看合并结果（Tab 按返回顺序动态追加）
4. 点进详情：
   - **磁力 / 种子**：点「复制链接」复制到剪贴板，或用下载工具打开
   - **网盘**：看到网盘类型 + 提取码，复制链接去浏览器转存；某些站（BT影视/云集/SeedHub）需点「解析链接」先获取真实地址
   - **二维码类**（SeedHub）：需去源站扫码转存
5. 右上角齿轮进入设置：
   - 修改任意站点域名（站换域名后改这里）
   - 停用不用的站点（如被 CF 拦截的 SeedHub）
   - 开启 HTTP 代理（若设备走 Clash，填 `127.0.0.1:7890`）

## 文档

| 文档 | 说明 |
|---|---|
| [docs/站点对接文档.md](docs/站点对接文档.md) | 13 个站点逐一对接实现、踩坑记录、故障排查 |
| [docs/技术文档.md](docs/技术文档.md) | 应用架构、模块设计、技术栈、关键实现 |
| [docs/需求文档.md](docs/需求文档.md) | 产品需求、功能清单、非功能需求 |
| [docs/路线图.md](docs/路线图.md) | 版本规划、后续迭代方向 |

## 目录结构

```
search-hub/
├── android/                          # Android 工程
│   └── app/src/main/java/com/searchhub/app/
│       ├── MainActivity.kt
│       ├── ui/
│       │   ├── AppRoot.kt            # 入口(验证码宿主)
│       │   ├── AppViewModel.kt       # 全局状态(engine/repository/config)
│       │   ├── navigation/           # 导航
│       │   ├── search/               # 搜索页/详情页/ViewModel
│       │   ├── settings/             # 站点/代理设置
│       │   └── captcha/              # 验证码弹窗
│       └── data/
│           ├── HttpEngine.kt         # 网络层(UA/cookie/代理/GBK)
│           ├── SearchRepository.kt   # 聚合
│           ├── SiteAdapter.kt        # 适配器接口
│           ├── ConfigStore.kt        # 配置持久化
│           └── *Adapter.kt           # 每站一个适配器
├── docs/                             # 文档
└── .gitignore
```

## 免责声明

本项目仅作个人学习与技术研究，聚合的是第三方公开分享的资源索引。请遵守当地法律法规，仅下载自身拥有合法权利的资源，勿用于商业传播。但资源版权归原权利人所有。

## 许可证

Copyright © 2026 SearchHub Contributors

SearchHub 采用 **GNU General Public License v3.0（GPL-3.0）**。

**商业使用说明**：如果你希望将 SearchHub 用于闭源商业产品、企业内部系统捆绑或商业分发，你需要购买商业授权。请联系作者（通过 GitHub Issues）获取授权详情。

根据 GPL-3.0 的条款，你可以在遵守许可证的前提下自由使用、修改和分发；但任何修改后的版本必须以相同许可证发布，并且**商业闭源发布需要作者明确授权**。