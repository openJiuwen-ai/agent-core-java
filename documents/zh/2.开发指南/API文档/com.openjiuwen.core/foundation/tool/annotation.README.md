# annotation

`com.openjiuwen.core.foundation.tool.annotation` 当前只包含工具定义注解，用于把普通 Java 方法暴露为可扫描的工具入口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ToolDefinition`](annotation/ToolDefinition.md) | 标记工具方法，并声明工具名称、描述与是否自动提取参数 Schema。 |

## 说明

- 注解保留策略为 `RetentionPolicy.RUNTIME`，因此运行时反射可见。
- 目标元素类型为 `ElementType.METHOD`，不能直接标注在类或字段上。
