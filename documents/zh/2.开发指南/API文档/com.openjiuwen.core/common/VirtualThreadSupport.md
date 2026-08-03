# com.openjiuwen.core.common.VirtualThreadSupport

## 类 VirtualThreadSupport

```java
public final class VirtualThreadSupport
```

`VirtualThreadSupport` 是 SDK 的 JDK 版本兼容层，用于在 Java 17 编译基线上适配 JDK21 虚拟线程能力。它会在运行时探测虚拟线程 API：JDK21 及以上优先使用虚拟线程，JDK17 下回退到平台线程。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static boolean isVirtualThreadSupported()` | 判断当前运行时是否提供 JDK 虚拟线程 API。JDK17 下返回 `false`。 |
| `public static boolean isCurrentThreadVirtual()` | 判断当前线程是否为虚拟线程。JDK17 下返回 `false`。 |
| `public static boolean isVirtual(Thread thread)` | 判断指定线程是否为虚拟线程；传入 `null` 或运行时不支持虚拟线程时返回 `false`。 |
| `public static ExecutorService newThreadPerTaskExecutor()` | 创建每任务一个线程的执行器；JDK21+ 下使用虚拟线程执行器，JDK17 下回退到 cached 平台线程池。 |
| `public static ExecutorService newThreadPerTaskExecutor(String namePrefix)` | 创建带命名前缀的每任务一个线程执行器；JDK21+ 下创建命名虚拟线程，JDK17 下创建命名 daemon 平台线程。 |
| `public static Thread startThread(Runnable task)` | 启动一个线程执行任务；JDK21+ 下使用虚拟线程，JDK17 下使用 daemon 平台线程。 |
| `public static Thread startThread(String threadName, Runnable task)` | 启动一个命名线程执行任务；JDK21+ 下使用命名虚拟线程，JDK17 下使用命名 daemon 平台线程。 |

## 运行时行为

| 运行时 | `newThreadPerTaskExecutor(...)` | `startThread(...)` | `isVirtual(...)` |
| --- | --- | --- | --- |
| JDK17 | 平台线程 fallback | daemon 平台线程 | 返回 `false` |
| JDK21+ | 虚拟线程 | 虚拟线程 | 调用运行时虚拟线程判断 |

`newThreadPerTaskExecutor(String namePrefix)` 在两类运行时下都会保留线程命名前缀。JDK21+ 下线程名形如 `namePrefix-1`、`namePrefix-2`；JDK17 下使用同样的前缀规则创建 daemon 平台线程。

## 使用建议

- SDK 内部异步、流式输出和 IO 任务应优先使用该类，而不是直接调用 `Thread.ofVirtual()` 或 `Executors.newVirtualThreadPerTaskExecutor()`。
- 业务应用只有在需要与 SDK 的 Java 17/JDK21 行为保持一致时才需要直接使用该类。
- 调用方仍然负责关闭 `ExecutorService`；该类只负责创建与运行时匹配的执行器。
- 该类不会改变阻塞调用本身的语义。使用虚拟线程后，仍应避免无界任务提交和不可控的外部资源占用。

## 相关测试

- `VirtualThreadSupportTest`

