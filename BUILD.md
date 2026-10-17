# chat 破解版 · 原生 Android 客户端

包名 `xfan.chat.app`，应用名 **chat破解版**，零第三方依赖（只用 Android SDK 自带 API 与系统 `org.json`）。

## 目录

```
app/
├── AndroidManifest.xml
├── build.gradle              模块构建配置
├── settings.gradle           工程配置
├── .github/workflows/        GitHub Actions 自动构建
├── src/xfan/chat/app/*.java  13 个源文件
├── res/                      布局 / 配色 / 图标
└── BUILD.md                  本文件
```

服务端需要同时上传 `appapi.php`（项目根目录，随本包附送），后台「APP」栏目配置版本与下载链接。

---

# 推荐方案：GitHub Actions 云构建（不用电脑、不用 SDK）

**这是最省事的路子。** 手机浏览器就能操作，全程不用跑 sh 文件，也不用装任何 SDK。

## 步骤

### 1. 建仓库

GitHub 网页 → New repository → 名字随意（如 `chat-app`）→ 勾选 Public → Create。

### 2. 传代码

在仓库页面点 **Add file → Upload files**，把 `app/` 目录里**全部内容**拖进去上传。
注意要包含 `.github/` 这个隐藏目录（如果网页不显示隐藏目录，就先建一个普通文件保证目录非空的视觉效果，Actions 文件可以稍后在网页上手动新建）。

> 如果 `.github` 传不上去，可以在网页上手动创建：
> Add file → Create new file → 路径填 `.github/workflows/build.yml` → 内容粘贴本仓库里那个同名文件 → Commit。

### 3. 触发构建

上传完会自动触发。也可以手动：Actions 页 → Build APK → Run workflow。

### 4. 取 APK

跑完（约 3-5 分钟）→ 点进这次运行 → 底部 **Artifacts** 区 → `chat-apk` → 下载。

下载的 zip 里就是 `app-debug.apk`，可直接安装。

### 5. 之后每次更新

改完代码重新上传覆盖，Actions 自动出新包。

---

# 备选方案 A：有 SDK 的电脑

```bash
gradle :app:assembleDebug
```

或用 Android Studio 打开这个目录直接 Run。产物：`app/build/outputs/apk/debug/app-debug.apk`

---

# 备选方案 B：Termux 手动构建

## 先说清楚坑

Termux 是 **aarch64**，而 Google 官方 Android SDK build-tools **只有 x86_64 的 Linux 二进制**。
`aapt2` 和 `zipalign` 是 native 程序，在纯 Termux 里会报：

```
cannot execute binary file: Exec format error
```

这不是权限问题，是 CPU 架构不匹配，`chmod +x` 也救不了。

要绕过去，得用 **proot + qemu 模拟 x86_64**：

```bash
pkg install proot-distro
proot-distro install debian
proot-distro login debian -- bash -c "apt update && apt install -y qemu-user-static openjdk-17-jdk unzip"
```

进去后再装 x86_64 的 SDK。可行，但编译一次要几分钟（qemu 指令翻译很慢）。

## 依赖清单

真正需要 5 样：

| 文件 | 类型 | aarch64 原生可跑 |
|---|---|---|
| `android.jar` | SDK platform（android-33） | 是（只是 class 库） |
| `ecj.jar` | Eclipse 编译器 | 是（纯 Java） |
| `d8.jar` | dex 编译器（r8 仓库） | 是（纯 Java） |
| `apksigner.jar` | 签名（apksig） | 是（纯 Java） |
| `aapt2` | 资源编译 | **否，需 x86_64 + qemu** |
| `zipalign` | 对齐 | **否，需 x86_64 + qemu** |

## 常见报错与处理

### `Unable to access jarfile ecj.jar`

**原因**：当前目录下没有这个文件。jar 得先下载，且命令要在 jar 所在目录执行。

**处理**：

```bash
mkdir -p ~/build && cd ~/build
```

```bash
curl -sL -o ecj.jar https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar
```

```bash
ls -lh ecj.jar
```

确认有大小（约 3MB）再继续。如果 403 或大小为 0，换镜像：

```bash
curl -sL -o ecj.jar https://maven.aliyun.com/repository/public/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar
```

**注意**：后面所有 `java -jar xxx.jar` 都要 `cd ~/build` 之后再跑，且用**相对路径**写源码位置，别在源码目录里执行。

### `android.jar` 找不到

```bash
curl -sL -o android.jar https://github.com/Sable/android-platforms/raw/master/android-33/android.jar
```

或任何提供 android-33 platform 的源。

### `Bad class file magic` / `version 61`

JDK 版本和 ecj 的 `-8` 参数不匹配。ecj 的 `-8` 表示输出 Java 8 字节码，改成 `-11` 或去掉。

## 构建步骤（逐条复制，不要写成 sh）

假定：`android.jar`、`ecj.jar`、`d8.jar`、`apksigner.jar`、`aapt2`、`zipalign` 都在 `~/build/`，源码在 `~/chat/app/`。

**1. 编译 Java → class**

```bash
cd ~/build && rm -rf classes && mkdir classes
```

```bash
java -jar ecj.jar -8 -bootclasspath android.jar -d classes -cp android.jar ~/chat/app/src/xfan/chat/app/*.java
```

**2. class → dex**

```bash
cd ~/build && java -jar d8.jar --lib android.jar --output . $(find classes -name '*.class')
```

得到 `classes.dex`。

**3. 编译资源**

```bash
cd ~/build && aapt2 compile --dir ~/chat/app/res -o res.zip
```

```bash
cd ~/build && aapt2 link res.zip -I android.jar --manifest ~/chat/app/AndroidManifest.xml --java gen -o app-unsigned.apk
```

**4. 塞 dex 进 apk**

```bash
cd ~/build && zip -ur app-unsigned.apk classes.dex
```

**5. 对齐**

```bash
cd ~/build && zipalign -f -p 4 app-unsigned.apk app-aligned.apk
```

**6. 生成密钥（只需一次）**

```bash
cd ~/build && keytool -genkeypair -v -keystore chat.keystore -alias chat -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -dname "CN=xfan, OU=chat, O=chat, L=., S=., C=CN"
```

**7. 签名**

```bash
cd ~/build && java -jar apksigner.jar sign --ks chat.keystore --ks-key-alias chat --ks-pass pass:123456 --out chat.apk app-aligned.apk
```

产物 `~/build/chat.apk`。

---

# APK 放哪给客户端下载

传到网站任意可访问位置（如 `https://你的域名/chat/chat.apk`），
然后后台 → **APP** 栏目填：

- 版本名（如 `1.1`）
- 版本码（如 `2`，**必须比上一版大**，客户端靠它判断是否更新）
- 下载链接（APK 完整 URL）
- 更新内容（弹窗显示文字）

保存后客户端每次启动自动比对，服务器版本码更大就弹更新提示，点「下载更新」跳浏览器下载。

---

# 功能对照

| 功能 | 实现位置 |
|---|---|
| 登录 / 注册（邀请码、IP 绑定） | `LoginActivity`，走 `appapi.php` |
| 三页：聊天 / 好友 / 我的 | `MainActivity` 底部三个 tab |
| 左侧边栏（主题 / 气泡 / 名称颜色 / 签到 / 兑换 / 公告） | `MainActivity` 抽屉 |
| 频道消息、私聊、轮询 | `ChatFragment` |
| 频道列表、加入频道、好友列表 | `FriendsFragment` |
| 好友长按：置顶 / 删除（红色确认弹窗） | `FriendsFragment` |
| 好友申请：发送 / 同意 / 拒绝 | `FriendsFragment` |
| 发消息、发图片、我的上传、撤回 | `ChatFragment` |
| 我的资料、签到、邀请码、简介、改名、头像、退出 | `MeFragment` |
| 启动检查更新，跳转浏览器下载 | `UpdateManager` |

> 网页端的用户信息原本在左侧边栏底部，这里按要求移到了底部「我的」栏目。
