# output_parsers

`com.openjiuwen.core.foundation.llm.output_parsers` converts raw assistant output into structured JSON or Markdown representations.

## Core Types

| Type | Description |
| --- | --- |
| [`BaseOutputParser`](output_parsers/BaseOutputParser.md) | Base class for parsing LLM output into the desired format. |
| [`JsonOutputParser`](output_parsers/JsonOutputParser.md) | JSON output parser that extracts JSON from LLM text output. |
| [`MarkdownContent`](output_parsers/MarkdownContent.md) | Structured representation of Markdown content. |
| [`MarkdownElement`](output_parsers/MarkdownElement.md) | Single Markdown element with positional metadata. |
| [`MarkdownElementType`](output_parsers/MarkdownElementType.md) | Markdown element type constants. |
| [`MarkdownOutputParser`](output_parsers/MarkdownOutputParser.md) | Markdown output parser that extracts structured elements from LLM output. |

## Notes

- `JsonOutputParserTest` covers plain JSON, fenced JSON blocks, and streaming chunk assembly.
