# com.openjiuwen.core.single_agent.agents.ReActAgentConfig

## 类 ReActAgentConfig

```java
public class ReActAgentConfig
```

`ReActAgent` 的运行配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `memScopeId` | `String` | `""` | 长期记忆作用域 ID。 |
| `modelName` | `String` | `""` | 模型名称。 |
| `modelProvider` | `String` | `"openai"` | 模型供应商标识。 |
| `apiKey` | `String` | `""` | 模型服务 API Key。 |
| `apiBase` | `String` | `""` | 模型服务 API Base。 |
| `promptTemplateName` | `String` | `""` | 预设提示模板名称。 |
| `promptTemplate` | `List<Map<String, String>>` | `new ArrayList<>()` | 系统提示模板列表。 |
| `maxIterations` | `int` | `5` | ReAct 最大迭代次数。 |
| `modelClientConfig` | `ModelClientConfig` | `-` | 显式模型客户端配置。 |
| `modelConfigObj` | `ModelRequestConfig` | `-` | 模型请求配置对象。 |
| `sysOperationId` | `String` | `-` | 供技能能力初始化使用的 SysOp 标识。 |
| `contextEngineConfig` | `ContextEngineConfig` | `ContextEngineConfig.builder().maxContextMessageNum(200).defaultWindowRoundNum(10).build()` | 上下文窗口与 reload 行为配置。 |
| `contextProcessors` | `List<Object>` | `-` | 自定义上下文处理器列表。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public ReActAgentConfig configureModel(String modelName)` | 设置 `modelName`。 |
| `public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase)` | 设置模型供应商与访问凭据。 |
| `public ReActAgentConfig configurePrompt(String promptName)` | 设置 `promptTemplateName`。 |
| `public ReActAgentConfig configurePromptTemplate(List<Map<String, String>> promptTemplate)` | 直接设置系统提示模板列表。 |
| `public ReActAgentConfig configureContextEngine( Integer maxContextMessageNum, Integer defaultWindowRoundNum, boolean enableReload )` | 依据窗口大小与 reload 开关构造 `ContextEngineConfig`。 |
| `public ReActAgentConfig configureMemScope(String memScopeId)` | 设置长期记忆作用域 ID。 |
| `public ReActAgentConfig configureMaxIterations(int maxIterations)` | 设置最大迭代次数。 |
| `public ReActAgentConfig configureModelClient( String provider, String apiKey, String apiBase, String modelName, boolean verifySsl )` | 一次性设置 `ModelClientConfig`、模型名和请求配置。 |
| `public ReActAgentConfig configureContextProcessors(List<Object> processors)` | 设置上下文处理器列表。 |

## 说明

- 相关测试：`ReActAgentConfigTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`。
- 该类型使用 Lombok 生成 builder / getter / setter；默认 `modelProvider` 为 `openai`，默认 `maxIterations` 为 `5`。
