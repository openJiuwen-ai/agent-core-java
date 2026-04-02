# com.openjiuwen.core.foundation.llm.schema.BaseModelInfo

## 类 BaseModelInfo

```java
public class BaseModelInfo
```

描述模型标识与基本元信息。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private static final String GREATER_THAN_ZERO_MESSAGE =` | 保存 `GREATER_THAN_ZERO_MESSAGE` 相关状态或配置。 |
| `private String apiKey =` | 保存 `apiKey` 相关状态或配置。 |
| `private String apiBase` | 保存 `apiBase` 相关状态或配置。 |
| `private String modelName =` | 保存 `modelName` 相关状态或配置。 |
| `private Double temperature = 0.95` | 保存 `temperature` 相关状态或配置。 |
| `private Double topP = 0.1` | 保存 `topP` 相关状态或配置。 |
| `private boolean streaming = false` | 保存 `streaming` 相关状态或配置。 |
| `private int timeout = 60` | 保存 `timeout` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseModelInfo(String apiKey, String apiBase, String modelName, Double temperature, Double topP, Boolean streaming, Integer timeout, Map<String, Object> extraFields) {` | 构造 `BaseModelInfo` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> getExtraFields() {` | 返回 `extraFields` 属性。 |
| `public void setExtraField(String key, Object value) {` | 设置 `extraField` 属性。 |
| `public void setTimeout(int timeout) {` | 设置 `timeout` 属性。 |
| `public void setExtraFields(Map<String, Object> extraFields) {` | 设置 `extraFields` 属性。 |

## 说明

- 所有签名均以当前 Java 源码为准。
