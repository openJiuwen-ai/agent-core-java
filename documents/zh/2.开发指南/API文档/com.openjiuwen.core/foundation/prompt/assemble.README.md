# assemble

`com.openjiuwen.core.foundation.prompt.assemble` 提供模板装配入口 `PromptAssembler`，负责从模板内容中提取输入键、校验初始化变量并执行占位符替换。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`PromptAssembler`](assemble/PromptAssembler.md) | 对字符串模板或消息列表模板执行装配。 |
| [`variables`](assemble/variables.README.md) | 提供抽象变量类型以及字符串、字典结构的具体实现。 |

## 说明

- 消息列表模板中，仅 `String` 内容或首元素为 `Map` 的非空列表内容会进入变量格式化流程。
- 通过 `promptAssemble` 调用时，多余键会被忽略，缺失键会补回占位符原文。
