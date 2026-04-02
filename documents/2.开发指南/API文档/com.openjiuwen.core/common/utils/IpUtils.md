# com.openjiuwen.core.common.utils.IpUtils

## class IpUtils

```java
public final class IpUtils
```

`IpUtils` discovers the local outbound IPv4 address using a UDP socket probe.

## Constructors

| Signature | Description |
| --- | --- |
| `private IpUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String getLocalIp()` | Connect a datagram socket to `8.8.8.8:80` and return the local interface address, or `127.0.0.1` when probing fails. |

## Notes

- The method excludes explicit loopback probing in the happy path and only falls back to loopback when socket setup fails.
