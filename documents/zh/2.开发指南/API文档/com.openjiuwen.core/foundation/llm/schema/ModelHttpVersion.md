# com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion

## 枚举 ModelHttpVersion

```java
public enum ModelHttpVersion
```

声明模型客户端可显式指定的 HTTP 版本枚举。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private final String value` | 对外序列化使用的配置值。 |
| `private final HttpClient.Version jdkVersion` | 对应的 JDK `HttpClient.Version` 枚举值。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue() {` | 返回序列化后的 HTTP 版本配置值。 |
| `public HttpClient.Version toJdkVersion() {` | 转换为 JDK `HttpClient.Version`。 |
| `public static ModelHttpVersion fromValue(String value) {` | 从配置字符串解析为枚举，兼容常见别名写法。 |

## 枚举值

| 名称 | 说明 |
| --- | --- |
| `HTTP_1_1` | 强制使用 HTTP/1.1。 |
| `HTTP_2` | 强制使用 HTTP/2。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `fromValue(...)` 兼容 `HTTP_1_1`、`HTTP/1.1`、`1.1`、`HTTP_2`、`HTTP/2`、`2`、`2.0` 等写法。
- 未配置 `httpVersion` 时，框架不会显式调用 `HttpClient.Builder.version(...)`，保持 JDK 默认协商行为。
