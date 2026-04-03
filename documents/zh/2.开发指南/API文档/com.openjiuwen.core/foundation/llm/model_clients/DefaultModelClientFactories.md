# com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories

## 类 DefaultModelClientFactories

```java
public final class DefaultModelClientFactories
```

集中注册内置 provider 工厂，供 `Model` 的 SPI 注册表使用。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private static volatile boolean registered` | 保存 `registered` 相关状态或配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static synchronized void ensureRegistered() {` | 确保内置 provider 工厂已注册到注册表。 |

## 说明

- 所有签名均以当前 Java 源码为准。
