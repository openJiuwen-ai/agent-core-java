# com.openjiuwen.core.foundation.llm.schema.ModelConfig

## 记录 ModelConfig

```java
public record ModelConfig( String modelProvider, BaseModelInfo modelInfo )
```

组合 provider 与 model 信息的轻量 record 配置对象。

## 记录组件

| 声明 | 说明 |
| --- | --- |
| `String modelProvider` | 保存 `modelProvider` 相关状态或配置。 |
| `BaseModelInfo modelInfo` | 保存 `modelInfo` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ModelConfig(String modelProvider) {` | 构造 `ModelConfig` 实例。 |

## 说明

- 所有签名均以当前 Java 源码为准。
