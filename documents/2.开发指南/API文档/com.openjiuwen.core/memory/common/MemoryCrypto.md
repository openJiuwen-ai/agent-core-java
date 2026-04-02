# com.openjiuwen.core.memory.common.MemoryCrypto

## 类 MemoryCrypto

```java
public final class MemoryCrypto
```

`MemoryCrypto` 是 `com.openjiuwen.core.memory.common` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `NONCE_LENGTH` | `int` | 字段 `NONCE_LENGTH`。 |
| `TAG_LENGTH` | `int` | 字段 `TAG_LENGTH`。 |
| `AES_KEY_LENGTH` | `int` | 字段 `AES_KEY_LENGTH`。 |
| `BIT_LENGTH` | `int` | 字段 `BIT_LENGTH`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static String[] encrypt(byte[] key, String plaintext)` | 执行 `encrypt`。 |
| `public static String decrypt(byte[] key, String ciphertext, String nonce, String tag)` | 执行 `decrypt`。 |

## 使用说明

- 相关测试：`MemoryCryptoTest.java`
