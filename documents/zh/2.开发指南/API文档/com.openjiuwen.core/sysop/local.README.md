# local

`com.openjiuwen.core.sysop.local` 提供本地文件系统、shell、代码执行实现，以及子进程调用、流式事件和环境准备所需的辅助类型。

## 类型

| Type | Description |
| --- | --- |
| [`InvokeData`](local/InvokeData.md) | 封装一次性子进程调用的 stdout、stderr、退出码和异常。 |
| [`LocalCodeOperation`](local/LocalCodeOperation.md) | 基于 `ProcessBuilder` 的本地代码执行实现。 |
| [`LocalFsOperation`](local/LocalFsOperation.md) | 基于 Java NIO 的本地文件系统实现。 |
| [`LocalShellOperation`](local/LocalShellOperation.md) | 基于系统 shell 的本地命令执行实现。 |
| [`OperationUtils`](local/OperationUtils.md) | 提供临时文件、环境变量和 `ProcessHandler` 工厂辅助方法。 |
| [`ProcessHandler`](local/ProcessHandler.md) | 负责监控子进程输出、超时和退出状态。 |
| [`StreamEvent`](local/StreamEvent.md) | 表示流式输出中的单个事件。 |
| [`StreamEventType`](local/StreamEventType.md) | 定义 `stdout`、`stderr`、`exit`、`error` 四类事件类型。 |

## 说明

- `LocalCodeOperationTest`、`LocalFsOperationTest`、`LocalShellOperationTest` 和 `LocalUtilsTest` 覆盖了本地执行路径的主要行为。
- 本包依赖的结果对象统一收敛在 `result` 子包中。
