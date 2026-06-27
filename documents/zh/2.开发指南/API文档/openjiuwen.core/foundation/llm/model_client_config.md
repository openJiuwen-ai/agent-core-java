# openjiuwen.core.foundation.llm 模型客户端配置

Java 对应包：`com.openjiuwen.core.foundation.llm`

本文说明 0.1.14 中模型客户端配置的对外字段。模型调用方通常通过 `ModelClientConfig` 配置连接信息，通过 `ModelRequestConfig` 配置单次模型请求的默认参数。

## ModelClientConfig

`ModelClientConfig` 用于描述模型服务连接方式，常用字段包括：

- `client_provider`：模型服务提供方，例如 `openai`、`openrouter`、`dashscope`、`siliconflow`。
- `api_key`：模型服务鉴权密钥。
- `api_base`：模型服务基础地址。
- `timeout`：连接和请求超时时间，单位为秒。
- `max_retries`：最大重试次数。
- `verify_ssl`：是否校验 HTTPS 证书。默认值为 `true`。
- `ssl_cert`：可选的自定义证书路径。
- `custom_headers`：附加 HTTP 头。
- `http_version`：可选的 HTTP 协议版本。

## http_version

`http_version` 用于指定模型客户端建连时优先使用的 HTTP 协议版本。当前支持：

- `HTTP_1_1`
- `HTTP/1.1`
- `1.1`
- `HTTP_2`
- `HTTP/2`
- `2`
- `2.0`

字段未配置时，客户端沿用 JDK `HttpClient` 的默认协议协商行为。该字段目前已接入 OpenAI、DashScope、SiliconFlow 等基于 Java `HttpClient` 的模型客户端。

示例：

```java
ModelClientConfig config = ModelClientConfig.builder()
        .clientProvider("openai")
        .apiKey("sk-xxx")
        .apiBase("https://api.openai.com/v1")
        .verifySsl(true)
        .httpVersion(ModelHttpVersion.HTTP_1_1)
        .build();
```

JSON 配置示例：

```json
{
  "client_provider": "openai",
  "api_key": "sk-xxx",
  "api_base": "https://api.openai.com/v1",
  "verify_ssl": true,
  "http_version": "HTTP_1_1"
}
```

## SSL 配置语义

`verify_ssl=true` 表示启用 HTTPS 证书校验。未配置 `ssl_cert` 时，客户端使用 JVM 默认信任库；配置 `ssl_cert` 时，客户端尝试加载指定证书。

`verify_ssl=false` 会关闭证书校验，仅建议用于本地调试或受控测试环境。

## BaseModelInfo

`BaseModelInfo` 同样支持 `http_version` 字段。该字段不会落入 `extraFields`，而是解析为强类型 `ModelHttpVersion`。

示例：

```json
{
  "model_name": "gpt-4o-mini",
  "api_base": "https://api.openai.com/v1",
  "api_key": "sk-xxx",
  "http_version": "2"
}
```

## ReActAgentConfig.configureModelClient

`ReActAgentConfig.configureModelClient` 新增带 `ModelHttpVersion` 的重载：

```java
new ReActAgentConfig().configureModelClient(
        "openai",
        "sk-xxx",
        "https://api.openai.com/v1",
        "gpt-4o-mini",
        true,
        ModelHttpVersion.HTTP_2
);
```

未传入 `ModelHttpVersion` 时，保持原有默认建连行为。
