# com.openjiuwen.core.common.utils.HashUtil

## class HashUtil

```java
public final class HashUtil
```

`HashUtil` 用于根据 API 凭证与提供方信息生成稳定的 SHA-256 哈希键。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String generateKey(String apiKey, String apiBase, String modelProvider)` | 将三个输入值排序后拼接，使用 UTF-8 编码计算 SHA-256，并返回十六进制字符串。 |
| `public static String generateKey(String apiKey, String apiBase)` | 使用默认 `modelProvider = "openai"` 生成哈希键。 |

## 说明

- 由于输入数组在哈希前会排序，因此相同元素集合即使传入顺序不同，结果也保持一致。
- 若运行环境不支持 `SHA-256`，方法会抛出 `RuntimeException`。
