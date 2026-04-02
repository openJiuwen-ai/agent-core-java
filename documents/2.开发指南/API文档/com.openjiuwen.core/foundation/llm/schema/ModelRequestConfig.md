# com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig

## 类 ModelRequestConfig

```java
public class ModelRequestConfig
```

描述 modelName、temperature、topP、maxTokens 等请求参数。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private String modelName =` | 保存 `modelName` 相关状态或配置。 |
| `private Double temperature = 0.95` | 保存 `temperature` 相关状态或配置。 |
| `private Double topP = 0.1` | 保存 `topP` 相关状态或配置。 |
| `private Integer maxTokens` | 保存 `maxTokens` 相关状态或配置。 |
| `private String stop` | 保存 `stop` 相关状态或配置。 |
| `private String user` | 保存 `user` 相关状态或配置。 |
| `private Integer seed` | 保存 `seed` 相关状态或配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> getExtraFields() {` | 返回 `extraFields` 属性。 |
| `public void setExtraField(String key, Object value) {` | 设置 `extraField` 属性。 |

## 说明

- 所有签名均以当前 Java 源码为准。
