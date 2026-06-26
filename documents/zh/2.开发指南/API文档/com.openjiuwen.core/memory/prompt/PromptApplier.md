# com.openjiuwen.core.memory.prompts.PromptApplier

## 类 PromptApplier

```java
public class PromptApplier
```

该类从类路径加载提示词模板并完成变量替换。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `PROMPT_RESOURCE_DIR` | `String` | 字段 `PROMPT_RESOURCE_DIR`。 |
| `instance` | `PromptApplier` | 单例实例。 |
| `promptCache` | `ConcurrentHashMap<String, PromptTemplate>` | 字段 `promptCache`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static PromptApplier getInstance()` | 返回单例实例。 |
| `public String apply(String filePrefix, Map<String, Object> variables)` | 执行 `apply`。 |
| `public void clearCache(String filePrefix)` | 执行 `clearCache`。 |
| `public void clearCache()` | 执行 `clearCache`。 |
| `public PromptTemplate getTemplate(String filePrefix)` | 返回 `getTemplate` 的执行结果。 |

## 使用说明

- 相关测试：`PromptApplierTest.java`
