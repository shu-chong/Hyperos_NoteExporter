# 笔记导出 (NoteExporter)

一个 MiuiX 风格的 Android 应用：把小米笔记（`com.miui.notes`）的本地备份解析出来，一键导出成 Word / PDF 文档。

## 功能

1. 打开应用后引导用户到「本地备份」单独备份笔记
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

## 备份文件结构

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

## 致谢

本应用的界面基于 [MiuiX](https://github.com/compose-miuix-ui/miuix) 构建
——一个使用 Jetpack Compose Multiplatform 实现的 MIUI/HyperOS 风格 UI 组件库（Apache-2.0 许可证）。
项目中的 Card、Button、对话框、进度条等界面组件均来自 MiuiX，感谢 MiuiX 的作者与贡献者。
