# prompt

`com.openjiuwen.core.foundation.prompt` 提供提示词模板对象、占位符装配器，以及面向字符串与结构化消息内容的变量替换能力。

## 包概览

| 名称 | 说明 |
| --- | --- |
| [`PromptTemplate`](prompt/PromptTemplate.md) | 保存模板内容并提供格式化、消息列表转换能力。 |
| [`assemble`](prompt/assemble.README.md) | 负责占位符收集、变量校验与运行时模板装配。 |

## 关键行为

- `PromptTemplate.content` 明确支持 `String` 与 `List<BaseMessage>` 两类内容。
- 缺失的占位符不会默认报错，而是保留为原占位符文本。
- `PromptAssembleTest` 覆盖了部分替换、点路径访问、自定义分隔符、结构化消息内容替换等场景。
