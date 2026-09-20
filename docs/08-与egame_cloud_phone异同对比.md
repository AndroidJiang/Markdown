# AntFluid 与 egame_cloud_phone Markdown 模块异同对比

> 本文档对比 AntFluid 开源版与 egame_cloud_phone 中实际使用的 fork 版 markdown 模块的差异，帮助理解两个项目之间的关系和演进方向。

---

## 一、关系概述

```
FluidMarkdown (蚂蚁集团开源)
    └── AntFluid (Android 原版)
            └── egame_cloud_phone (中国电信云手机 fork)
                 └── 持续修改增强，已显著演进
```

**引入方式：** egame_cloud_phone 将 AntFluid 的整套 markwon 模块**复制为工程内 Gradle 子模块**，模块名和包名（`com.fluid.afm`、`io.noties.markwon`）与 AntFluid 一致，在此基础上持续修改。

**不是 Maven 依赖，而是源码 fork。**

---

## 二、模块对应关系

### 2.1 共同模块

以下模块两个项目都有，结构基本一致：

| 模块 | AntFluid | egame_cloud_phone | 差异程度 |
|------|----------|-------------------|----------|
| fluid-markdown | ✅ | ✅ | ★★☆ 有增强 |
| markwon-core | ✅ | ✅ | ★★☆ 有修改 |
| markwon-ext-tables | ✅ | ✅ | ★★★ 大量修改 |
| markwon-ext-latex | ✅ | ✅ | ★☆☆ 少量 |
| markwon-ext-strikethrough | ✅ | ✅ | ☆☆☆ 基本相同 |
| markwon-ext-tasklist | ✅ | ✅ | ☆☆☆ 基本相同 |
| markwon-html | ✅ | ✅ | ★☆☆ 少量 |
| markwon-image | ✅ | ✅ | ★☆☆ 少量 |
| markwon-inline-parser | ✅ | ✅ | ☆☆☆ 基本相同 |
| markwon-syntax-highlight | ✅ | ✅ | ★☆☆ 深色适配 |

### 2.2 独有模块

| 模块 | AntFluid | egame_cloud_phone |
|------|----------|-------------------|
| app-sample | ✅ Demo App | ❌ |
| app (主应用) | ❌ | ✅ |
| lib_base | ❌ | ✅ 基础库 |
| aichat | ❌ | ✅ WebSocket AI 对话 |

---

## 三、核心差异详解

### 3.1 表格模块 — 差异最大

| 功能点 | AntFluid | egame_cloud_phone |
|--------|----------|-------------------|
| 代码行数 | ~813 行 | ~1158 行 |
| 表头标题栏 | 默认**显示** | 默认**隐藏**（`isHideHeader=true`） |
| 表格内链接点击 | ❌ 不支持 | ✅ `handleCellLinkClick()` |
| 长链接截断 | ❌ | ✅ `compactTableLinkText()` |
| 单元格文本清理 | ❌ | ✅ `sanitizeTableCellText()` |
| MovementMethod | `MarkdownAwareMovementMethod` | 新增 `NonScrollingLinkMovementMethod` |
| RecyclerView 复用 | 缓存可能残留 | 非流式时 `clear()` 清全部缓存 |

**egame 新增的关键方法：**

```java
// TableRowSpan.java
public boolean handleCellLinkClick(int x, int y) {
    // 根据坐标定位到具体 cell 的 StaticLayout
    // 查找 ClickableSpan 并触发点击
}

// TablePlugin.java
private String sanitizeTableCellText(String text) {
    // 清理单元格中的 ReplacementSpan 可能导致的不折行问题
}

private String compactTableLinkText(String text) {
    // 截断过长的链接文本，避免撑破列宽
}
```

### 3.2 流式渲染

| 功能点 | AntFluid | egame_cloud_phone |
|--------|----------|-------------------|
| 基础打字机 | `startPrinting` / `appendPrinting` | 同上 |
| 流式 Markdown API | ❌ | ✅ `renderStreamingMarkdown` |
| 完成流式渲染 | ❌ | ✅ `finishStreamingMarkdown` |
| 重置流式状态 | ❌ | ✅ `resetStreamingMarkdown` |
| 图片流式快照 | ❌ | ✅ `startImageStreaming` / `stopImageStreaming` |

**egame 新增的流式 API：**

```java
// PrinterMarkDownTextView.java (egame 版)
public void renderStreamingMarkdown(String markdown) {
    // 流式 Markdown 渲染（非打字机，直接显示当前全部内容）
    // 用于 AI 对话中服务端推送完整响应片段
}

public void finishStreamingMarkdown() {
    // 标记流式结束，执行最终清理
}

public void resetStreamingMarkdown() {
    // 重置状态，准备接收新的流式内容
}
```

### 3.3 图片处理

| 功能点 | AntFluid | egame_cloud_phone |
|--------|----------|-------------------|
| ImageHandler | 接口定义 | 同上 + `MyImageHandler` (Glide 实现) |
| 图片尺寸 | 默认 16:9 裁剪 | 支持正方形完整展示 |
| 图片流式 | 无 | `startImageStreaming` / `stopImageStreaming` 避免闪烁 |
| AsyncDrawable 缓存 | 基础 | 增强缓存策略 |

### 3.4 代码高亮

| 功能点 | AntFluid | egame_cloud_phone |
|--------|----------|-------------------|
| 高亮主题 | 默认浅色 | 支持**深色主题**适配 |
| SyntaxHighlightPlugin | 标准 Prism4j | 增加深色配色方案 |
| 代码块背景 | 白色 | 可配置深色背景 |
| CommonMarkdownConfig | ❌ | ✅ 统一配置类 |

### 3.5 业务集成层

egame_cloud_phone 在 `app` 模块中有完整的业务集成代码：

| 文件 | 路径 | 作用 |
|------|------|------|
| `AiChatContentParser.kt` | app/.../aichat/parser/ | WebSocket 流式文本 → ChatItem 调度 |
| `TextViewHolderFactory.kt` | app/.../aichat/adapter/factory/ | RecyclerView 中创建 Markdown 卡片 |
| `ClawProcessViewHolderFactory.kt` | app/.../aichat/adapter/factory/ | "Claw 过程"卡片（流式推理展示） |
| `MyImageHandler.java` | app/.../aichat/markdown/ | Glide 图片加载实现 |
| `CommonMarkdownConfig.kt` | app/.../aichat/ | 统一 Markdown 配置（样式/深色主题） |

---

## 四、已知 Fork 修改记录

egame_cloud_phone 项目中 `docs/markdown源码修改范围.md` 记录了 13 项已知修改：

| 序号 | 修改内容 | 涉及模块 |
|------|----------|----------|
| 1 | 表格样式定制（颜色/边框/圆角） | markwon-ext-tables |
| 2 | 表格内链接点击支持 | markwon-ext-tables |
| 3 | 代码块深色主题适配 | markwon-syntax-highlight |
| 4 | 行内代码折行问题修复 | markwon-core |
| 5 | 列表 RecyclerView 复用截断修复 | markwon-ext-tables |
| 6 | 图片异步加载闪烁修复 | markwon-image |
| 7 | 有序列表序号重叠修复 | markwon-core |
| 8 | 流式表格链接文本截断 | markwon-ext-tables |
| 9 | 表格单元格 ReplacementSpan 折行修复 | markwon-ext-tables |
| 10 | 图片尺寸完整展示 | markwon-image |
| 11 | 流式 Markdown 新 API | fluid-markdown |
| 12 | 图片流式快照 API | fluid-markdown |
| 13 | NonScrollingLinkMovementMethod | markwon-ext-tables |

---

## 五、如何在 egame 中参考 AntFluid 修改

### 5.1 同步上游更新

当 AntFluid 上游有更新时：

```
1. 对比 AntFluid 新版与 egame 当前版本的 diff
2. 评估每个变更是否与 egame 的定制修改冲突
3. 手动合并不冲突的变更
4. 冲突部分逐个解决
```

### 5.2 在 AntFluid 中验证修改

AntFluid 的 `app-sample` 提供了独立的验证环境：

```
1. 在 AntFluid 中修改 markwon-* 模块代码
2. 运行 app-sample 验证效果
3. 确认无问题后，将修改同步到 egame_cloud_phone
```

### 5.3 修改对照表

当需要在 egame 中修改表格功能时，可以对照以下文件：

| AntFluid 文件 | egame 对应文件 | 说明 |
|---------------|---------------|------|
| `markwon-ext-tables/.../TablePlugin.java` | 同路径 | egame 版更长，有额外方法 |
| `markwon-ext-tables/.../TableRowSpan.java` | 同路径 | egame 有 `handleCellLinkClick` |
| 无 | `markwon-ext-tables/.../NonScrollingLinkMovementMethod.java` | egame 独有 |
| `fluid-markdown/.../PrinterMarkDownTextView.java` | 同路径 | egame 有流式 Markdown API |

---

## 六、开发建议

### 6.1 新功能开发流程

```
1. 先在 AntFluid app-sample 中原型验证
2. 确认方案可行后，在 egame_cloud_phone 中正式实现
3. 考虑 egame 已有的定制修改是否受影响
4. 更新 egame 的 docs/markdown源码修改范围.md
```

### 6.2 修改范围控制

- **仅改样式** → 修改 `MarkdownStyles` / `MarkwonTheme` 相关代码，影响最小
- **改 Span 行为** → 修改 markwon-ext-* 中的 Span 类，中等影响
- **改 Plugin 逻辑** → 修改 Plugin 类或 `MarkdownParserFactory`，需要测试全部渲染场景
- **改 Parser** → 修改 CommonMark 扩展或 BlockParser，影响最大，需要回归测试所有 Markdown 语法

### 6.3 测试清单

修改 markdown 模块后，需要验证：

```
□ 静态渲染：各类 Markdown 元素正常显示
□ 流式渲染：打字机效果正常
□ RecyclerView：列表中复用正常
□ 深色主题（egame）：代码块/背景色正确
□ 表格：边框/圆角/链接点击
□ LaTeX：行内/块级公式
□ 代码块：语法高亮/复制按钮
□ 图片：加载/点击/尺寸
□ 链接：点击/图标
□ 列表：嵌套层级/bullet 样式
```

---

## 下一步阅读

- [项目架构与设计思路](./01-项目架构与设计思路.md) — 回顾 AntFluid 整体架构
- [集成指南](./07-集成指南.md) — 在新项目中集成 AntFluid
