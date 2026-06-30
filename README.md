# CallBlocker — Android 来电拦截

基于 `CallScreeningService` API 的 Android 来电拦截应用，无需 Root，系统级拦截，无后台常驻烦恼。

## 功能

- **来电拦截** — 黑名单中的号码自动拒接，不响铃不通知
- **白名单放行** — 白名单号码无条件通过，不受黑名单影响
- **拦截记录** — 查看被拦截的来电详情（号码、时间、来源）
- **黑/白名单管理** — 支持手动添加号码、从拦截记录一键加入
- **号码状态标注** — 首页来电列表标记各号码的黑/白/未处理状态
- **冲突防护** — 同一个号码不会同时出现在黑名单和白名单中
- **间隔保护** — 同一号码被拦截后，N 分钟内不再重复拦截
- **中/英文双语** — 跟随系统语言自动切换
- **设置内容丰富** — 拦截开关、间隔时间、拦截提示自定义

## 隐私说明

**无需联网，数据全在本地。** CallBlocker 不需要也不需要申请任何网络权限：
- 所有数据（黑名单、白名单、拦截记录）仅存储在手机本地数据库中
- 不会向任何服务器发送任何数据
- 无广告、无追踪、无分析统计
- 可开启飞行模式正常使用

## 截图

（待补充）

## 下载

右侧 [Releases](https://github.com/visitor257/CallBlocker/releases) 页面下载最新 APK。

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

## 本地开发

### 环境要求

- Android Studio Hedgehog (2023.1.1+) 或 IntelliJ IDEA
- JDK 17
- Android SDK 34
- Gradle 8.5

### 构建

```bash
git clone https://github.com/visitor257/CallBlocker.git
cd CallBlocker
./gradlew assembleDebug
```

APK 生成位置：`app/build/outputs/apk/debug/app-debug.apk`

## 权限说明

| 权限 | 用途 |
|---|---|
| `BIND_SCREENING_SERVICE` | 来电拦截核心权限（系统绑定） |
| `READ_PHONE_STATE` | 读取 SIM 卡信息（诊断用） |
| `READ_CONTACTS` | （可选）读取联系人名称 |

应用首次启动后，需要前往 **设置 → 应用管理 → 特殊权限 → 来电拦截** 中手动启用 CallBlocker 的拦截权限（Android 系统限制，应用无法自动申请）。

## License

MIT
