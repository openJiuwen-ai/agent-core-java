# com.openjiuwen.core.memory.common.KvPrefixRegistry

## 类 KvPrefixRegistry

```java
public final class KvPrefixRegistry
```

`KvPrefixRegistry` 是 `com.openjiuwen.core.memory.common` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `INSTANCE` | `KvPrefixRegistry` | 字段 `INSTANCE`。 |
| `allPrefixes` | `Set<String>` | 字段 `allPrefixes`。 |
| `currentPrefixes` | `Set<String>` | 字段 `currentPrefixes`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static KvPrefixRegistry getInstance()` | 返回单例实例。 |
| `public synchronized void registerCurrent(String prefix)` | 执行 `registerCurrent`。 |
| `public synchronized void registerLegacy(String prefix)` | 执行 `registerLegacy`。 |
| `public synchronized Set<String> getAllPrefixes()` | 返回 `getAllPrefixes` 的执行结果。 |
| `public synchronized void unregister(String prefix)` | 执行 `unregister`。 |
