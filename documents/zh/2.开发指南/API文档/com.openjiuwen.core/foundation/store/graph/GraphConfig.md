# com.openjiuwen.core.foundation.store.graph.GraphConfig

## class GraphConfig

```java
public class GraphConfig
```

图存储总配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `uri` | `String` | `-` | 图存储地址，不能为空白。 |
| `name` | `String` | `""` | 配置名称。 |
| `user` | `String` | `""` | 用户名。 |
| `password` | `String` | `""` | 密码。 |
| `token` | `String` | `""` | 令牌。 |
| `backend` | `String` | `"milvus"` | 默认后端名称。 |
| `timeout` | `double` | `15.0` | 连接或校验超时时间，单位秒。 |
| `extras` | `Map<String, Object>` | `{}` | 附加配置，键必须为字符串。 |
| `workerThreads` | `int` | `30` | 工作者线程数。 |
| `embedDim` | `int` | `512` | 默认嵌入维度，要求至少为 `32`。 |
| `embedBatchSize` | `int` | `10` | 默认嵌入批大小，要求至少为 `1`。 |
| `embeddingCls` | `Class<? extends Embedding>` | `null` | 嵌入实现类型。 |
| `embeddingConfig` | `EmbeddingConfig` | `null` | 嵌入配置。 |
| `dbStorageConfig` | `GraphStoreStorageConfig` | `new GraphStoreStorageConfig()` | 存储字段容量配置。 |
| `dbEmbedConfig` | `GraphStoreIndexConfig` | `new GraphStoreIndexConfig()` | 图索引配置。 |
| `wipeAtStartup` | `boolean` | `false` | 是否在启动时清空数据。 |
| `requestMaxRetries` | `int` | `5` | 请求最大重试次数。 |
| `requestRetryWait` | `double` | `0.1` | 请求重试等待时长，单位秒。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getUri()` | 返回图存储地址。 |
| `public String getName()` | 返回配置名称。 |
| `public String getUser()` | 返回用户名。 |
| `public String getPassword()` | 返回密码。 |
| `public String getToken()` | 返回令牌。 |
| `public String getBackend()` | 返回后端名称。 |
| `public double getTimeout()` | 返回超时时间。 |
| `public Map<String, Object> getExtras()` | 返回附加配置。 |
| `public int getWorkerThreads()` | 返回工作线程数。 |
| `public int getEmbedDim()` | 返回默认嵌入维度。 |
| `public int getEmbedBatchSize()` | 返回默认嵌入批大小。 |
| `public Class<? extends Embedding> getEmbeddingCls()` | 返回嵌入实现类型。 |
| `public EmbeddingConfig getEmbeddingConfig()` | 返回嵌入配置。 |
| `public GraphStoreStorageConfig getDbStorageConfig()` | 返回存储字段配置。 |
| `public GraphStoreIndexConfig getDbEmbedConfig()` | 返回图索引配置。 |
| `public boolean isWipeAtStartup()` | 返回是否启动时清空数据。 |
| `public int getRequestMaxRetries()` | 返回最大重试次数。 |
| `public double getRequestRetryWait()` | 返回重试等待时长。 |
| `public static Builder builder()` | 创建构建器。 |

## 使用说明

- 构建时会校验基础参数，并执行 URI 有效性检查。
- 当 `uri` 看起来像文件路径时，会尝试创建父目录；当它是网络地址时，会尝试在 `timeout` 范围内建立 socket 连接，失败只记录日志。
- 构建器类型 `Builder` 通过 `builder()` 返回，用于链式设置各项配置。
