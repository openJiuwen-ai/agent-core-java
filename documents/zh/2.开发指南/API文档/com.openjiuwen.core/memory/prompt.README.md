# prompt

`com.openjiuwen.core.memory.prompt` 提供记忆相关提示词模板的加载与变量替换能力，供更新判定、记忆提取等流程生成模型输入。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`PromptApplier`](./prompt/PromptApplier.md) | 单例提示词应用器，从类路径读取 `.md` 模板并执行变量替换。 |

## 相关测试

- `PromptApplierTest`
