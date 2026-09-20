# 扩展新的 Markdown 元素指南

> 本文档介绍如何在 AntFluid 中添加全新的 Markdown 语法元素，从定义语法到最终渲染，覆盖完整流程和需要修改的文件。

---

## 一、扩展方式概览

Markwon 提供四种扩展新元素的方式，按复杂度递增排列：

| 方式 | 适用场景 | 复杂度 |
|------|----------|--------|
| **A. 文本级正则匹配** | 简单的文本模式替换（如 Emoji `:alias:`） | ★☆☆ |
| **B. Span 替换/追加** | 修改已有 Node 的渲染方式 | ★★☆ |
| **C. HTML 标签扩展** | 自定义 HTML 标签（如 `<icon>`） | ★★☆ |
| **D. 新 CommonMark Node** | 全新 Markdown 语法（如 `$\oiint$`） | ★★★ |

---

## 二、方式 A — 文本级正则匹配

### 适用场景

在 `Text` 节点中按正则匹配特定模式，替换为 Span。不需要修改解析器。

### 已有示例

`AfmTextPlugin` 中的脚注处理：

```java
// fluid-markdown/.../text/AfmTextPlugin.java
builder.on(Text.class, (visitor, text) -> {
    String literal = text.getLiteral();
    // 正则匹配 [^index]
    Matcher m = FOOTNOTE_PATTERN.matcher(literal);
    if (m.find()) {
        // 在匹配位置设置 FootnoteSpan
        SpannableBuilder.setSpans(visitor.builder(),
            new FootnoteSpan(...), start, end);
    }
});
```

### 实战：添加自定义高亮语法 `==text==`

**需要修改的文件：**

1. `fluid-markdown/src/main/java/com/fluid/afm/markdown/` — 新建 Plugin 或在现有 Plugin 中添加

**步骤：**

```java
// 1. 创建 Plugin
public class HighlightPlugin extends AbstractMarkwonPlugin {

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(Text.class, (visitor, text) -> {
            String literal = text.getLiteral();
            Pattern p = Pattern.compile("==(.+?)==");
            Matcher m = p.matcher(literal);

            int lastEnd = 0;
            while (m.find()) {
                // 追加匹配前的普通文本
                if (m.start() > lastEnd) {
                    visitor.builder().append(literal.substring(lastEnd, m.start()));
                }
                // 追加高亮文本 + Span
                int start = visitor.builder().length();
                visitor.builder().append(m.group(1));
                SpannableBuilder.setSpans(visitor.builder(),
                    new BackgroundColorSpan(0x40FFFF00), start, visitor.builder().length());
                lastEnd = m.end();
            }
            // 追加剩余文本
            if (lastEnd < literal.length()) {
                visitor.builder().append(literal.substring(lastEnd));
            }
        });
    }
}

// 2. 在 MarkdownParserFactory.getDefaultPlugins() 中注册
plugins.add(new HighlightPlugin());
```

---

## 三、方式 B — Span 替换/追加

### 适用场景

不改变解析逻辑，只修改某个已有 Node 类型对应的 Span。

### SpanFactory 操作方式

```java
// 替换：完全覆盖已有 SpanFactory
builder.setFactory(Link.class, (config, props) -> {
    return new MyCustomLinkSpan(...);
});

// 追加：在已有 Span 之后再添加
builder.appendFactory(Heading.class, (config, props) -> {
    return new UnderlineSpan();  // 给标题额外加下划线
});

// 前置：在已有 Span 之前添加
builder.prependFactory(Emphasis.class, (config, props) -> {
    return new ForegroundColorSpan(Color.RED);
});
```

### 已有示例

`MarkdownParserFactory.linkPlugin()` 将默认 `Link` Span 替换为 `LinkClickSpan`：

```java
public static AbstractMarkwonPlugin linkPlugin(ElementClickEventCallback callback) {
    return new AbstractMarkwonPlugin() {
        @Override
        public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
            builder.setFactory(Link.class, (config, props) -> {
                String href = CoreProps.LINK_DESTINATION.require(props);
                return new LinkClickSpan(config.theme(), href, callback);
            });
        }
    };
}
```

### 需要修改的文件

| 文件 | 操作 |
|------|------|
| 新建或修改 Plugin | 实现 `configureSpansFactory` |
| `MarkdownParserFactory.java` | 在 `getDefaultPlugins()` 中注册 |

---

## 四、方式 C — HTML 标签扩展

### 适用场景

Markdown 中嵌入自定义 HTML 标签（如 `<icon src="...">`, `<iconlink href="...">`）。

### 核心接口

```java
// markwon-html/.../TagHandler.java
public abstract class TagHandler {
    public abstract void handle(
        @NonNull MarkwonVisitor visitor,
        @NonNull MarkwonHtmlRenderer renderer,
        @NonNull HtmlTag tag);
}
```

### 已有示例

`IconSpanHandler` 处理 `<icon>` 标签：

```java
// fluid-markdown/.../icon/IconSpanHandler.java
public class IconSpanHandler extends TagHandler {
    @Override
    public void handle(MarkwonVisitor visitor, MarkwonHtmlRenderer renderer, HtmlTag tag) {
        String src = tag.attributes().get("src");
        String width = tag.attributes().get("width");
        // 创建图标 Span 并设置到 builder
        IconSpan span = new IconSpan(src, parsedWidth, parsedHeight);
        SpannableBuilder.setSpans(visitor.builder(), span, start, end);
    }
}
```

### 注册方式

在 `CustomHtmlPlugin` 或 `MarkdownParserFactory.htmlPlugin()` 中注册：

```java
// fluid-markdown/.../html/CustomHtmlPlugin.java
@Override
public void configureHtmlRenderer(MarkwonHtmlRenderer.Builder builder) {
    builder.setHandler("icon", new IconSpanHandler());
    builder.setHandler("iconlink", new IconLinkSpanHandler(callback));
    // ... 其他自定义标签
}
```

### 实战：添加 `<badge>` 标签

**需要修改的文件：**

| 文件 | 操作 |
|------|------|
| 新建 `BadgeSpan.java` | 自定义 ReplacementSpan，绘制徽章 |
| 新建 `BadgeTagHandler.java` | 继承 TagHandler，解析属性创建 Span |
| `CustomHtmlPlugin.java` | 注册 `builder.setHandler("badge", new BadgeTagHandler())` |

```java
// 1. BadgeSpan.java — 自绘圆角背景 + 白色文字
public class BadgeSpan extends ReplacementSpan {
    private final String text;
    private final int bgColor;

    @Override
    public void draw(Canvas canvas, ...) {
        // 绘制圆角矩形背景
        RectF rect = new RectF(x, top, x + width, bottom);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        // 绘制文字
        canvas.drawText(text, x + padding, y, textPaint);
    }
}

// 2. BadgeTagHandler.java
public class BadgeTagHandler extends TagHandler {
    @Override
    public void handle(..., HtmlTag tag) {
        String color = tag.attributes().get("color");
        int bgColor = Color.parseColor(color != null ? color : "#FF4444");
        // ... 创建 BadgeSpan
    }
}

// 3. CustomHtmlPlugin.java 中注册
builder.setHandler("badge", new BadgeTagHandler());
```

使用效果：`这是一个<badge color="#FF4444">新功能</badge>标签`

---

## 五、方式 D — 新 CommonMark Node（最完整）

### 适用场景

定义全新的 Markdown 语法，需要在 Parser 阶段就识别。

### 已有示例

`OiintInlineProcessor` 处理 `$\oiint$` 数学符号：

```
markwon-inline-parser/.../inline/OiintInlineProcessor.java
```

### 完整步骤

以添加 `:::warning` 告警块语法为例：

```markdown
:::warning
这是一段警告内容
:::
```

**需要创建/修改的文件：**

| 文件 | 类型 | 操作 |
|------|------|------|
| `WarningNode.java` | 新建 | 自定义 AST 节点 |
| `WarningBlockParser.java` | 新建 | 解析 `:::warning` 语法 |
| `WarningSpan.java` | 新建 | 自绘告警块 UI |
| `WarningPlugin.java` | 新建 | 注册 Parser + Visitor + SpanFactory |
| `MarkdownParserFactory.java` | 修改 | 在插件链中注册 |

```java
// 1. 定义 AST 节点
public class WarningNode extends CustomBlock {
    private final String type; // "warning", "info", "error"
    public WarningNode(String type) { this.type = type; }
    public String getType() { return type; }
}

// 2. 块级解析器
public class WarningBlockParser extends AbstractBlockParser {
    private final WarningNode node;

    @Override
    public Block getBlock() { return node; }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        String line = state.getLine().toString().trim();
        if (":::".equals(line)) {
            return BlockContinue.finished();  // 遇到结束标记
        }
        return BlockContinue.atIndex(state.getIndex());
    }

    public static class Factory extends AbstractBlockParserFactory {
        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matched) {
            String line = state.getLine().toString().trim();
            if (line.startsWith(":::")) {
                String type = line.substring(3).trim();
                return BlockStart.of(new WarningBlockParser(
                    new WarningNode(type.isEmpty() ? "info" : type)))
                    .atIndex(state.getNextNonSpaceIndex());
            }
            return BlockStart.none();
        }
    }
}

// 3. 自绘 Span
public class WarningSpan extends ReplacementSpan {
    private final String type;
    // draw() 中绘制带颜色边框的告警块
}

// 4. Plugin 串联一切
public class WarningPlugin extends AbstractMarkwonPlugin {
    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        // 注册块解析器
        builder.customBlockParserFactory(new WarningBlockParser.Factory());
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(WarningNode.class, (visitor, node) -> {
            int length = visitor.length();
            visitor.visitChildren(node);
            visitor.setSpansForNodeOptional(node, length);
        });
    }

    @Override
    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
        builder.setFactory(WarningNode.class, (config, props) -> {
            return new WarningSpan("warning");
        });
    }
}

// 5. 在 MarkdownParserFactory 中注册
plugins.add(new WarningPlugin());
```

---

## 六、扩展方式选择决策树

```
你要实现什么？
│
├── 在已有文本中匹配特殊模式？
│   └── 方式 A：文本级正则匹配
│
├── 修改已有 Markdown 元素的渲染外观？
│   └── 方式 B：Span 替换/追加
│
├── 处理自定义 HTML 标签？
│   └── 方式 C：HTML 标签扩展
│
└── 定义全新的 Markdown 语法？
    └── 方式 D：新 CommonMark Node
```

---

## 七、扩展后的注册清单

无论使用哪种方式，都需要确保 Plugin 被注册到插件链中：

```
MarkdownParserFactory.getDefaultPlugins()  — 业务级插件
        ↓
MarkdownParser.getDefaultPlugins()         — 引擎级插件
        ↓
MarkwonImpl                                — 按顺序执行所有插件
```

### 注意事项

1. **插件顺序有意义** — `processMarkdown` 按注册顺序执行，后注册的 Plugin 看到的是前面处理过的文本
2. **SpanFactory 覆盖** — `setFactory` 会替换已有工厂，`appendFactory`/`prependFactory` 是追加
3. **流式渲染兼容** — 如果新元素需要感知流式状态，实现 `StreamOutStateObserver` 接口
4. **configureVisitor 中 null** — `builder.on(Node.class, null)` 可以禁用某个节点的默认处理

---

## 下一步阅读

- [基于已有元素做自定义扩展](./04-基于已有元素自定义扩展指南.md) — 更多修改已有行为的场景
- [表格模块深度剖析](./05-表格模块深度剖析.md) — 深入理解最复杂的内置元素
