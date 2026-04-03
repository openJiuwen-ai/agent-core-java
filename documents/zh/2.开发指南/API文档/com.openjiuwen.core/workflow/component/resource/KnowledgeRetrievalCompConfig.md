# com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalCompConfig

## 类 KnowledgeRetrievalCompConfig

```java
public class KnowledgeRetrievalCompConfig extends ComponentConfig
```

知识检索组件配置模型，声明知识库、检索、向量存储和可选模型参数。

## 字段

| 签名 | 说明 |
| --- | --- |
| `private List<KnowledgeBaseConfig> kbConfigs` | 知识库配置列表。 |
| `private RetrievalConfig retrievalConfig` | 检索配置。 |
| `private VectorStoreConfig vectorStoreConfig` | 向量存储配置。 |
| `private Map<String, Object> vectorStoreAdditionalConfig` | 向量存储附加参数。 |
| `private EmbeddingConfig embedConfig` | Embedding 配置。 |
| `private String modelId` | 模型 ID。 |
| `private ModelClientConfig modelClientConfig` | 模型客户端配置。 |
| `private ModelRequestConfig modelConfig` | 模型请求配置。 |
| `private String resultSeparator = "\n\n"` | 检索结果拼接分隔符。 |
| `private boolean includeMetadata = false` | 是否输出检索元数据。 |

## 说明

- 该类型使用 Lombok 生成部分访问器或构造方法，文档仅记录源码中显式定义的字段与方法。
