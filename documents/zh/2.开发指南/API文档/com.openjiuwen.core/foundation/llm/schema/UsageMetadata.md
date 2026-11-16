# com.openjiuwen.core.foundation.llm.schema.UsageMetadata

## 类 UsageMetadata

```java
public class UsageMetadata
```

描述 token 计数与用量统计信息。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private int code = 0` | 保存 `code` 相关状态或配置。 |
| `private String errMsg =` | 保存 `errMsg` 相关状态或配置。 |
| `private String prompt =` | 保存 `prompt` 相关状态或配置。 |
| `private String taskId =` | 保存 `taskId` 标识。 |
| `private String modelName =` | 保存 `modelName` 相关状态或配置。 |
| `private double totalLatency = 0.0` | 保存 `totalLatency` 相关状态或配置。 |
| `private String firstTokenTime =` | 保存 `firstTokenTime` 相关状态或配置。 |
| `private String requestStartTime =` | 保存 `requestStartTime` 相关状态或配置。 |
| `private Integer inputTokens` | 输入 token；null 表示字段缺失，显式 0 表示真实零值。 |
| `private Integer outputTokens` | 输出 token；null 表示字段缺失，显式 0 表示真实零值。 |
| `private Integer totalTokens` | 总 token；null 表示字段缺失，显式 0 表示真实零值。 |
| `private Integer cacheTokens` | 输入中命中缓存的 token；null 表示字段缺失，显式 0 表示真实零值。 |

## 说明

- 既有 `getInputTokens()` 等 primitive getter 在字段缺失时仍返回 0；需要区分缺失时，使用对应的
  `get...OrNull()` 可空访问器。
- OpenAI 兼容解析器只在响应含 `usage` 对象时创建 `UsageMetadata`；`usage` 缺失或为 null 时，
  `AssistantMessage.usageMetadata` 为 null。对象内各 token 字段独立保留在场性，不用 0 补齐缺失字段。
- `OpenAIModelClient` 的流式请求默认写入 `stream_options.include_usage=true`，显式 false 会被保留；
  是否实际返回 usage 仍由服务端决定。空 `choices` 的 usage-only 帧会保留并参与聚合。
  独立的 `SiliconFlowModelClient` 不自动添加该请求选项，调用方显式传入时仍可透传。
- 所有签名均以当前 Java 源码为准。
