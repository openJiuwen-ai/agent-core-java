# com.openjiuwen.core.memory.manage.update.MemUpdateChecker

## 类 MemUpdateChecker

```java
public class MemUpdateChecker
```

该类借助提示词模板与模型输出来判定新旧记忆的冗余与冲突关系。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `promptApplier` | `PromptApplier` | 提示词模板应用器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MemUpdateChecker()` | 创建 `MemUpdateChecker` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel)` | 执行 `check`。 |
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel, int retries)` | 执行 `check`。 |

## 使用说明

- 相关测试：`MemUpdateCheckerTest.java`
