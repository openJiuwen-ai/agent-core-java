# com.openjiuwen.core.memory.manage.update.CheckResult

## 枚举 CheckResult

```java
public enum CheckResult
```

`CheckResult` 是 `com.openjiuwen.core.memory.manage.update` 包下的公开枚举型，文档按 Java 源码列出其公开成员与签名。

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `REDUNDANT` | 表示新记忆与旧记忆冗余。 |
| `CONFLICTING` | 表示新旧记忆存在冲突。 |
| `NONE` | 表示无需执行额外动作。 |

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `value` | `String` | 原始值。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的原始值。 |
| `public static CheckResult fromValue(String value)` | 根据字符串值解析对应枚举。 |
