# com.openjiuwen.core.memory.process.extract.VariableResult

## 类 VariableResult

```java
public class VariableResult
```

`VariableResult` 表示单个记忆变量的抽取结果。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `variableKey` | `String` | 变量名。默认值为空字符串。 |
| `variableValue` | `String` | 变量值。默认值为空字符串。 |

## 使用说明

- 该类使用 Lombok 生成访问器、构建器与无参/全参构造方法。
- `MemoryAnalyzer` 负责创建该对象列表，`Generator` 会把非空变量值转换为 `VariableUnit`。
