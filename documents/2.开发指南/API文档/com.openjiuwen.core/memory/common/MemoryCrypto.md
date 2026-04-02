# com.openjiuwen.core.memory.common.MemoryCrypto

## class MemoryCrypto

```java
public final class MemoryCrypto
```

AES-256-GCM encryption/decryption utilities for memory content.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `NONCE_LENGTH` | `int` | nonce length. |
| `TAG_LENGTH` | `int` | tag length. |
| `AES_KEY_LENGTH` | `int` | aes key length. |
| `BIT_LENGTH` | `int` | bit length. |

## Constructors

| Signature | Description |
| --- | --- |
| `private MemoryCrypto()` | Create a new `MemoryCrypto` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String[] encrypt(byte[] key, String plaintext)` | Encrypt plaintext using AES-256-GCM. |
| `public static String decrypt(byte[] key, String ciphertext, String nonce, String tag)` | Decrypt ciphertext using AES-256-GCM. |

## Notes

- Related tests: `MemoryCryptoTest.java`
