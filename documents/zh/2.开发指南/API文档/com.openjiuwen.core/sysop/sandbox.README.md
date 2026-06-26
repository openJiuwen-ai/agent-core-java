# sandbox

`com.openjiuwen.core.sysop.sandbox` 提供沙箱模式操作包装器，以及面向远程执行的容器与网关占位类型。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`Container`](sandbox/Container.md) | 沙箱容器抽象的占位类型。 |
| [`ContainerManager`](sandbox/ContainerManager.md) | 沙箱模式下容器管理器的占位类型。 |
| [`SandboxClient`](sandbox/SandboxClient.md) | 沙箱客户端的占位类型。 |
| [`SandboxCodeOperation`](sandbox/SandboxCodeOperation.md) | 沙箱代码执行操作的占位实现，当前尚未实现。 |
| [`SandboxFsOperation`](sandbox/SandboxFsOperation.md) | 沙箱文件系统操作的占位实现，当前尚未实现。 |
| [`SandboxGateway`](sandbox/SandboxGateway.md) | 沙箱网关入口的占位类型。 |
| [`SandboxShellOperation`](sandbox/SandboxShellOperation.md) | 沙箱命令执行操作的占位实现，当前尚未实现。 |

## 说明

- 当前包同时包含沙箱操作桩类，以及容器与网关相关的占位类型。
- 源码明确标记为 stub 的类型当前尚未实现，文档不推断未来行为。
- `SandboxOperationTest` 目前只验证代表性操作桩类会抛出 `UnsupportedOperationException`。
