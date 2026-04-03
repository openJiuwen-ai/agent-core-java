# output_parsers

`com.openjiuwen.core.foundation.llm.output_parsers` 将模型文本输出解析为 JSON 或 Markdown 结构结果。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseOutputParser`](output_parsers/BaseOutputParser.md) | 输出解析器基类，约定格式说明与解析入口。 |
| [`JsonOutputParser`](output_parsers/JsonOutputParser.md) | 从模型文本输出中提取 JSON 并转换为结构化结果。 |
| [`MarkdownContent`](output_parsers/MarkdownContent.md) | 表示 Markdown 解析后的内容容器，用于组装结构化元素列表。 |
| [`MarkdownElement`](output_parsers/MarkdownElement.md) | 表示单个 Markdown 元素及其属性。 |
| [`MarkdownElementType`](output_parsers/MarkdownElementType.md) | 定义 Markdown 解析过程使用的元素类型。 |
| [`MarkdownOutputParser`](output_parsers/MarkdownOutputParser.md) | 将模型 Markdown 输出解析为结构化元素集合。 |

## 说明

- `JsonOutputParserTest` 覆盖 JSON 片段提取与解析流程。
