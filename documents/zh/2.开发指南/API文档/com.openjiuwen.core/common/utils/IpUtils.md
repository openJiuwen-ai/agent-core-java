# com.openjiuwen.core.common.utils.IpUtils

## class IpUtils

```java
public final class IpUtils
```

`IpUtils` 用于探测当前进程对外通信时使用的本机 IPv4 地址。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String getLocalIp()` | 通过 UDP socket 连接 `8.8.8.8:80` 获取本机出口地址；若失败则返回 `127.0.0.1`。 |

## 说明

- 该实现不会真正发送业务数据，只利用 socket 连接过程确定本机网络接口地址。
