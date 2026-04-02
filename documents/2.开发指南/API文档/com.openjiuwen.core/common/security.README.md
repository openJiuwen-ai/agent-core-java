# security

`com.openjiuwen.core.common.security` 提供安全相关的基础工具，覆盖异常摘要、JSON 安全读写、路径敏感性判断、SSL 上下文构建、URL 校验以及运行时安全配置加载。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`ExceptionUtils`](./security/ExceptionUtils.md) | 提供异常摘要格式化与根因展开工具。 |
| [`JsonUtils`](./security/JsonUtils.md) | 基于 Jackson 实现安全的 JSON 序列化与反序列化。 |
| [`PathChecker`](./security/PathChecker.md) | 基于 `UserConfig` 的敏感路径单例检查器。 |
| [`SslUtils`](./security/SslUtils.md) | 构造严格或非严格的 `SSLContext`，并解析 SSL 运行时配置。 |
| [`UrlUtils`](./security/UrlUtils.md) | 校验 URL 合法性，并处理代理与 `NO_PROXY` 逻辑。 |
| [`UserConfig`](./security/UserConfig.md) | 读取并缓存敏感路径相关配置。 |

## 说明

- `PathChecker` 与 `UserConfig` 共同决定路径是否视为敏感路径。
- `SslUtils` 和 `UrlUtils` 的行为会读取环境变量或系统属性，例如 `SAFE_CERT_DIR`、`NO_PROXY`、`http_proxy`、`https_proxy`、`SSRF_PROTECT_ENABLED` 与 `IS_SENSITIVE`。
