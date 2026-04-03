# com.openjiuwen.core.common.security.SslUtils

## class SslUtils

```java
public final class SslUtils
```

`SslUtils` 提供 HTTPS 场景下的 SSL 上下文构建与运行时 SSL 配置解析能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static SSLContext createStrictSslContext(String sslCertPath)` | 创建严格模式的 `SSLContext`；当提供证书路径时，会校验 `SAFE_CERT_DIR`、文件范围与文件大小，再加载 CA 证书并初始化信任管理器。 |
| `public static SSLContext createInsecureSslContext()` | 创建信任所有证书的 `SSLContext`，仅适用于显式关闭校验的场景。 |
| `public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv, List<String> triggerValues, boolean urlIsHttps)` | 按环境变量或系统属性解析 SSL 校验开关与证书路径，返回 `[sslVerify, sslCertPath]`。 |

## 说明

- 严格模式在 `sslCertPath` 不为 `null` 时会强制要求 `SAFE_CERT_DIR` 已配置，且证书文件真实路径必须位于允许目录下。
- 证书文件大小必须大于 `0` 且不超过 `1MB`，否则会抛出 `COMMON_SSL_CONTEXT_INIT_FAILED`。
- 当目标 URL 不是 HTTPS 时，`getSslConfig(...)` 直接返回 `false` 和 `null`。
