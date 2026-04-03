# config

`com.openjiuwen.core.sysop.config` 提供本地执行与沙箱网关使用的配置对象，分别描述工作目录策略、命令白名单和远端访问参数。

## 类型

| Type | Description |
| --- | --- |
| [`LocalWorkConfig`](config/LocalWorkConfig.md) | 描述本地执行的工作目录和命令白名单。 |
| [`SandboxGatewayConfig`](config/SandboxGatewayConfig.md) | 描述沙箱网关地址、通用请求参数和鉴权信息。 |

## 说明

- 这两个 DTO 都依赖 Lombok 生成访问器和构建器，字段表只记录源码中显式声明的字段。
- `LocalWorkConfigTest` 验证了本地配置的默认白名单和构建器行为。
