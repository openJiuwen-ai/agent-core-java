# com.openjiuwen.core.memory.manage.mem_model.MemoryType

## 枚举 MemoryType

```java
public enum MemoryType
```

`MemoryType` 是 `com.openjiuwen.core.memory.manage.mem_model` 包下的公开枚举型，文档按 Java 源码列出其公开成员与签名。

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `FRAGMENT_MEMORY` | 分片记忆。 |
| `VARIABLE` | 变量记忆。 |
| `SUMMARY` | 摘要记忆。 |
| `UNKNOWN` | 未知类型。 |

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `value` | `String` | 原始值。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的原始值。 |
| `public static MemoryType fromValue(String value)` | 根据字符串值解析对应枚举。 |
