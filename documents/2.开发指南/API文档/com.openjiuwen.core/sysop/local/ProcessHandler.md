# com.openjiuwen.core.sysop.local.ProcessHandler

## 类 ProcessHandler

```java
public class ProcessHandler
```

`ProcessHandler` 负责监控 Java 子进程的 stdout、stderr、退出状态和整体超时，并提供一次性调用与流式调用两种消费方式。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `process` | `Process` | - | 被监控的子进程实例。 |
| `chunkSize` | `int` | - | 每次从 stdout/stderr 读取的字符块大小。 |
| `encoding` | `Charset` | - | 读取输出时使用的字符编码。 |
| `overallTimeoutSeconds` | `int` | - | 整体执行超时时间，单位秒。 |
| `queue` | `BlockingQueue<StreamEvent>` | - | 流式模式下缓存 `StreamEvent` 的阻塞队列。 |
| `isExecuted` | `AtomicBoolean` | - | 保证 `invoke()` 与 `stream()` 只能二选一调用一次的标志。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ProcessHandler(Process process, int chunkSize, Charset encoding, int overallTimeoutSeconds)` | 使用显式块大小、编码和超时时间创建处理器。 |
| `public ProcessHandler(Process process)` | 使用默认参数创建处理器：块大小 `1024`、编码 `UTF-8`、超时 `300` 秒。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public InvokeData invoke()` | 一次性等待进程结束并收集完整 stdout、stderr 和退出码。 |
| `public Iterator<StreamEvent> stream()` | 以 `StreamEvent` 迭代器形式持续消费 stdout、stderr、错误与退出事件。 |

## 说明

- `invoke()` 与 `stream()` 互斥，任一实例只允许成功调用其中一个一次。
- 实现使用虚拟线程分别读取 stdout 和 stderr，避免因管道缓冲区写满导致子进程阻塞。
- 流式模式下，超时会强制销毁进程并返回 `ERROR` 事件；当 reader 线程结束且队列清空后会补发 `EXIT` 事件。
