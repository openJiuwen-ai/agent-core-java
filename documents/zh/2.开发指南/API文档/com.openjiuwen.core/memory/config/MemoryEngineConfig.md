# com.openjiuwen.core.memory.config.MemoryEngineConfig

## 类 MemoryEngineConfig

```java
public class MemoryEngineConfig
```

该类定义全局记忆引擎的系统参数。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultModelCfg` | `ModelRequestConfig` | 默认模型请求配置。 |
| `defaultModelClientCfg` | `ModelClientConfig` | 默认模型客户端配置。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void validateCryptoKey()` | 校验 `cryptoKey` 的长度约束。 |

## 使用说明

- 该类型通过 Lombok 生成 getter、setter 与 builder，文档仅列出显式声明的字段与公开方法。
