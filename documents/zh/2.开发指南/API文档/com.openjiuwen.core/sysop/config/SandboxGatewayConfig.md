# com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig

## 类 SandboxGatewayConfig

```java
public class SandboxGatewayConfig
```

`SandboxGatewayConfig` 描述沙箱网关访问所需的地址、通用参数和鉴权参数。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `gatewayUrl` | `String` | `""` | 沙箱网关服务地址。 |
| `params` | `Map<String, Object>` | 空 `HashMap` | 每次请求都会携带的通用参数。 |
| `authHeaders` | `Map<String, String>` | 空 `HashMap` | 发往沙箱网关时附带的鉴权 HTTP 头。 |
| `authQueryParams` | `Map<String, String>` | 空 `HashMap` | 发往沙箱网关时附带的鉴权查询参数。 |

## Lombok 说明

- 该类型使用 `Data`、`Builder`、`NoArgsConstructor`、`AllArgsConstructor` 生成访问器、构建器和构造辅助方法。
- 四个字段都通过 `@Builder.Default` 提供默认空值或空容器。

## 说明

- 当前任务范围内没有直接针对 `SandboxGatewayConfig` 的专门测试，字段语义直接来自源码声明。
