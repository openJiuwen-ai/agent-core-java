# sysop

`com.openjiuwen.core.sysop` 提供系统操作门面、基础抽象、运行模式以及工具 ID 组装能力，并把本地实现、注册表、结果对象和沙箱实现组织在同一文档树下。

## 子包

| Package | Description |
| --- | --- |
| [`config`](sysop/config.README.md) | 提供本地执行与沙箱网关共用的运行配置对象。 |
| [`local`](sysop/local.README.md) | 提供基于本地进程和 Java NIO 的文件、命令、代码执行实现。 |
| [`registry`](sysop/registry.README.md) | 提供 `@Operation` 注解、操作定义对象和注册中心。 |
| [`result`](sysop/result.README.md) | 提供系统操作统一结果封装、数据载荷和流式 chunk DTO。 |
| [`sandbox`](sysop/sandbox.README.md) | 提供沙箱模式下的文件、命令、代码执行包装类型。 |

## 类型

| Type | Description |
| --- | --- |
| [`BaseCodeOperation`](sysop/BaseCodeOperation.md) | 定义代码执行操作的抽象基类和标准工具暴露方式。 |
| [`BaseFsOperation`](sysop/BaseFsOperation.md) | 定义文件系统操作的抽象基类和统一方法签名。 |
| [`BaseOperation`](sysop/BaseOperation.md) | 统一封装操作名称、运行模式、描述和运行配置。 |
| [`BaseShellOperation`](sysop/BaseShellOperation.md) | 定义 shell 命令执行操作的抽象基类。 |
| [`FsConstants`](sysop/FsConstants.md) | 收集文件读取、上传、下载和流式读取使用的块大小常量。 |
| [`OperationMode`](sysop/OperationMode.md) | 表示 `LOCAL` 与 `SANDBOX` 两种系统操作运行模式。 |
| [`SysOperation`](sysop/SysOperation.md) | 按 `SysOperationCard` 配置装配并缓存具体操作实例的门面。 |
| [`SysOperationCard`](sysop/SysOperationCard.md) | 描述系统操作卡片元数据、运行模式以及本地/沙箱配置。 |
| [`SysOperationToolAdapter`](sysop/SysOperationToolAdapter.md) | 将 `SysOperation` 暴露的操作方法封装成 `LocalFunction` 工具。 |
| [`ToolIdProxy`](sysop/ToolIdProxy.md) | 便捷生成 `cardId.opType.methodName` 形式工具 ID 的辅助对象。 |

## 说明

- 根包源码定义了系统操作的公共抽象、模式枚举、门面对象以及工具适配入口。
- `OperationModeTest`、`SysOperationTest`、`SysOperationCardTest`、`SysOperationToolAdapterTest` 和本地执行测试覆盖了当前任务范围内的主要行为。
