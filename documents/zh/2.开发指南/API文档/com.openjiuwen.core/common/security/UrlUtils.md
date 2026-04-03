# com.openjiuwen.core.common.security.UrlUtils

## class UrlUtils

```java
public final class UrlUtils
```

`UrlUtils` 提供 URL 合法性校验以及全局代理解析能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void checkUrlIsValid(String url)` | 校验 URL 非空、协议为 `http`/`https`，并确保解析出的地址不是内网、本地回环、链路本地或任意本地地址；失败时抛出 `COMMON_URL_INPUT_INVALID`。 |
| `public static String getGlobalProxyUrl(String url)` | 读取 `http_proxy`、`https_proxy`、`HTTP_PROXY`、`HTTPS_PROXY` 中的第一个可用代理；若命中 `NO_PROXY` 规则则返回 `null`。 |
| `public static Map<String, String> getGlobalProxies(String url)` | 若存在全局代理，则同时返回 `http` 与 `https` 两个键对应的代理地址；否则返回 `null`。 |
| `public static boolean shouldBypassProxy(String url)` | 根据 `NO_PROXY`/`no_proxy` 配置判断当前 URL 是否应跳过代理。 |

## 说明

- URL 校验前会把路径中的 `{placeholder}` 片段替换为固定占位符，再交给 `URI` 解析。
- `NO_PROXY` 支持精确主机名、以 `.` 开头的域后缀、单个 IP 以及 CIDR 表达式。
- 当 `SSRF_PROTECT_ENABLED` 被设置为 `false` 时，内网地址检查会被关闭。
