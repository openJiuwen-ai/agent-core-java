# com.openjiuwen.core.memory.process.extract.MemoryAnalyzerResult

## 类 MemoryAnalyzerResult

```java
public class MemoryAnalyzerResult
```

`MemoryAnalyzerResult` 表示消息分析阶段的结构化输出。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `hasKeyInformation` | `boolean` | 标记当前消息中是否包含可提取的关键信息。默认值为 `false`。 |
| `variables` | `List<VariableResult>` | 提取出的变量结果列表。默认值为空列表。 |
| `summary` | `String` | 生成的摘要文本。默认值为空字符串。 |

## 使用说明

- 该类使用 Lombok 生成访问器、构建器与无参/全参构造方法。
- `Generator` 会读取其中的变量列表、摘要与 `hasKeyInformation` 标记，决定后续生成哪些记忆单元。
