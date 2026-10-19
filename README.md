# 笔记导出 (NoteExporter)

一个 MiuiX 风格的 Android 应用：把小米笔记（`com.miui.notes`）的本地备份解析出来，一键导出成 Word / PDF 文档。

## 功能

1. 打开应用后引导用户到「本地备份」单独备份笔记（内置跳转按钮）
2. 自动扫描 `MIUI/backup/AllBackup`，识别最新的备份文件（也可手动选择）
3. 一键导出到 `Download/小米笔记`：
   - `小米笔记导出.docx` / `小米笔记导出.pdf` — 全部笔记汇总（含目录、创建/修改时间、图片）
   - `单条笔记/NNN_日期_标题.docx` — 每条笔记一个文件（可开关）
   - 导出格式在设置里切换（Word / PDF）

## 构建

```bash
# JDK 21 + Android SDK (compileSdk 37)
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

签名配置从 `keystore.properties` 读取（该文件不在版本控制中）：

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

国内直连 `dl.google.com` 不稳定，`settings.gradle.kts` 里优先使用阿里云镜像。

## 技术要点

### 为什么需要本地备份

小米笔记的 `NotesProvider` 受签名级权限保护，普通应用（包括 adb shell）查询会得到：

```
SecurityException: uid 2000 does not have com.miui.notes.permission.ACCESS_NOTE
```

未 root 的设备只能走「本地备份」这条路：备份文件在共享存储上，有 `MANAGE_EXTERNAL_STORAGE` 就能读。

### 备份文件的三层结构

```
MIUI/backup/AllBackup/<时间戳>.zip
  └─ 笔记(com.miui.notes).bak        ← Android Backup 容器
       ├─ MIUI 头 (MIUI BACKUP\n2\ncom.miui.notes…)
       ├─ "ANDROID BACKUP\n5\n0\nnone\n"
       └─ tar
            ├─ miui_bak/_tmp_bak     ← 笔记数据（protobuf）
            ├─ miui_att/<sha1>       ← 图片附件（JPEG）
            └─ _manifest / _meta
```

对应实现：`core/BackupArchive.kt`（zip → 容器头 → tar）。

### protobuf 没有 .proto 定义

备份里的笔记是 protobuf，但小米没有公开 schema。`core/Proto.kt` 只按 wire type 做最小解析，
`core/NotesParser.kt` 按字段号取值：

| 版本 | 容器 | 正文 | 创建/修改 | 文件夹 |
|---|---|---|---|---|
| 新版 (应用 3.x) | 顶层 field 10 → 重复 field 2 | field 100（Markdown） | field 5 / 6 | field 10 |
| 旧版 (应用 2.x) | 顶层 field 6 → 重复 field 1/2 | field 9.4 或 field 8（MIUI XML） | field 5 / 6 | field 10 |

### 两个容易踩的坑

**1. 用户手写的代码会被当成标记删掉。**
正文里 `<feature name="LocalHdr">`、`<bool name="…">` 这类看着像 HTML 标签，其实是笔记内容本身。
必须先把 `&lt;` 反转义、再剥格式标记；顺序反了就会吃掉正文。

**2. 去重会误删。**
只按「纯文本相同」合并，会把「文字相同但图片不同」的笔记误判为重复。
`Exporter.dedupe()` 的比较键同时包含正文与图片 sha1 集合。

其余正文处理（Markdown 转义还原、U+2062 占位符、XML 非法字符）见 `core/NoteRenderer.kt`。

### DOCX 生成

`core/DocxWriter.kt` 手写 OOXML（不依赖任何第三方库）：
按需拼 `document.xml`，图片以关系（relationship）方式嵌入 `word/media/`。
同一图片重复引用会复用同一个媒体部件，避免文档膨胀。

## 文件结构

```
app/src/main/java/com/shu/notebackupexporter/
├── MainActivity.kt              界面（MiuiX 组件）
└── core/
    ├── ExportFormat.kt          导出格式枚举（Word / PDF）
    ├── Proto.kt                 protobuf 最小读取器
    ├── BackupArchive.kt         zip / Android Backup / tar 解析
    ├── NotesParser.kt           protobuf → Note
    ├── NoteRenderer.kt          Markdown 正文 → 文档
    ├── NoteDoc.kt               文档模型接口
    ├── DocxWriter.kt            OOXML 生成
    ├── PdfWriter.kt             PDF 生成
    ├── BackupLocator.kt         备份文件定位
    ├── Prefs.kt                 偏好存储
    └── Exporter.kt              导出流程编排
```
