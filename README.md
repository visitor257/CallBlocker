# CallBlocker — Android 来电拦截

基于 `CallScreeningService` API 的 Android 来电拦截应用，无需 Root，系统级拦截，无后台常驻烦恼。

## 功能

- **来电拦截** — 黑名单中的号码自动拒接，不响铃不通知
- **白名单放行** — 白名单号码无条件通过，不受黑名单影响
- **拦截记录** — 查看被拦截的来电详情（号码、时间、来源）
- **多 SIM 支持** — 任意数量 SIM 卡/ eSIM，每张卡独立设置
- **黑/白名单管理** — 支持手动添加号码、从拦截记录一键加入
- **冲突防护** — 同一个号码不会同时出现在黑名单和白名单中
- **中/英文双语** — 跟随系统语言自动切换
- **可自由配置** — 拦截开关、间隔保护，每张 SIM 独立

## 截图

（待补充）

## 下载

右侧 [Releases](https://github.com/) 页面下载最新 APK。

## 技术栈

| 组件 | 选用 |
|---|---|
| 拦截机制 | `CallScreeningService`（系统 API，无需 Root） |
| 架构 | MVVM + Repository |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room（SQLite） |
| 构建 | Gradle KTS |
| 最低版本 | Android 7.0（API 24） |
| 目标版本 | Android 14（API 34） |

## 项目结构

```
app/src/main/java/com/callblocker/app/
├── CallBlockerApp.kt          # Application 入口
├── data/
│   ├── dao/                   # Room DAO
│   │   ├── WhitelistDao.kt
│   │   ├── BlacklistDao.kt
│   │   ├── SimConfigDao.kt
│   │   └── CallRecordDao.kt
│   ├── db/
│   │   └── AppDatabase.kt     # 数据库定义 + Migration
│   ├── entity/                # 数据实体
│   │   ├── WhitelistEntry.kt
│   │   ├── BlockedNumber.kt
│   │   ├── SimConfig.kt
│   │   └── BlockedCallRecord.kt
│   └── repository/
│       └── CallBlockerRepository.kt
├── service/
│   └── CallBlockerService.kt  # 核心拦截服务
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt      # 首页（SIM 选择 + 拦截状态 + 记录抽屉）
│   │   ├── WhitelistScreen.kt # 白名单管理
│   │   ├── BlockedNumbersScreen.kt  # 黑名单管理
│   │   ├── SettingsScreen.kt  # 设置页面
│   │   └── MainScreen.kt      # 底部导航
│   └── theme/
│       └── Theme.kt           # 主题 + 颜色
└── util/
    └── SimUtils.kt            # SIM 卡工具
```

## 本地开发

### 环境要求

- Android Studio Hedgehog (2023.1.1+) 或 IntelliJ IDEA
- JDK 17
- Android SDK 34
- Gradle 8.5

### 构建

```bash
git clone <repo-url>
cd CallBlocker
./gradlew assembleDebug
```

APK 生成位置：`app/build/outputs/apk/debug/app-debug.apk`

## 权限说明

| 权限 | 用途 |
|---|---|
| `BIND_SCREENING_SERVICE` | 来电拦截核心权限（系统绑定） |
| `READ_PHONE_STATE` | 读取 SIM 卡信息 |
| `READ_CONTACTS` | （可选）读取联系人名称 |

应用首次启动后，需要前往 **设置 → 应用管理 → 特殊权限 → 来电拦截** 中手动启用 CallBlocker 的拦截权限（Android 系统限制，应用无法自动申请）。

## 版本历史

| 版本 | 内容 |
|---|---|
| v1.0.0 | 基础拦截功能 + 白名单 + 设置页 |
| v1.1.0 | 双 SIM 卡支持 |
| v1.2.0 | 黑名单功能 + 黑/白互转 + 手动添加 |
| v1.2.1 | 中/英文国际化 |
| v1.2.2 | 首页重构（记录收进抽屉）+ 号码三态标注 + 冲突防护 |

## License

MIT
