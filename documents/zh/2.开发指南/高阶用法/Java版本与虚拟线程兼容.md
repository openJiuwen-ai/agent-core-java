# Java 版本与虚拟线程兼容

openJiuwen Core Java 0.1.14 以 Java 17 作为编译基线，同时在 JDK21 及以上运行时自动使用虚拟线程能力。业务代码不需要为 JDK17 和 JDK21 分别维护两套 SDK 依赖，也不需要启用额外的 Maven profile。

## 版本边界

| 场景 | 行为 |
| --- | --- |
| Java 17 编译 | SDK 源码和字节码保持 Java 17 兼容，不直接引用 JDK21 专属 API。 |
| JDK17 运行 | SDK 通过兼容层回退到平台线程，相关判断方法返回非虚拟线程结果。 |
| JDK21+ 运行 | SDK 在运行时探测到虚拟线程 API 后，优先使用虚拟线程执行异步或 IO 任务。 |

SDK 不使用 multi-release JAR。虚拟线程 API 通过 `com.openjiuwen.core.common.VirtualThreadSupport` 在运行时探测并调用，因此同一份 Java 17 编译产物可以在 JDK17 和 JDK21+ 上运行。

## 公共兼容层

`VirtualThreadSupport` 是 SDK 暴露的 JDK 兼容层。SDK 内部的流式输出、工作流执行、检索、记忆、系统操作和部分扩展模块会通过它创建后台任务线程或任务执行器。

对业务侧来说，推荐只在确实需要与 SDK 行为对齐时使用它，例如：

```java
if (VirtualThreadSupport.isVirtualThreadSupported()) {
    // 当前运行时提供 JDK 虚拟线程 API
}

ExecutorService executor = VirtualThreadSupport.newThreadPerTaskExecutor("my-agent-io");
Thread thread = VirtualThreadSupport.startThread("my-agent-stream", task);
boolean virtual = VirtualThreadSupport.isCurrentThreadVirtual();
```

在 JDK17 下，上述代码不会触发 `NoSuchMethodError` 或 `ClassNotFoundException`。`newThreadPerTaskExecutor(...)` 会回退到平台线程池，`startThread(...)` 会启动 daemon 平台线程，`isCurrentThreadVirtual()` 返回 `false`。

## 文档用语

文档中出现“虚拟线程执行”时，如果对应实现经过 `VirtualThreadSupport`，应理解为“运行时自适应线程执行”：JDK21+ 下是虚拟线程，JDK17 下是平台线程 fallback。只有直接说明依赖 JDK21+ 的能力，才表示该行为在 JDK17 下不可用。

## 与 Spring Boot Demo 的关系

`agent-core-demo` 也按 Java 17 编译，并复用 SDK 的 `VirtualThreadSupport` 判断虚拟线程状态。Demo 中的：

```properties
spring.threads.virtual.enabled=true
```

表示允许 Spring Boot 在 JDK21+ 运行时启用虚拟线程。该配置不会让 JDK17 获得虚拟线程能力；在 JDK17 下，Spring Boot 会继续使用普通平台线程处理请求。

## 验证建议

SDK 侧可优先运行：

```bash
mvn "-Dtest=VirtualThreadSupportTest" test
```

Demo 侧可在工作区根目录运行：

```bash
mvn "-pl" "agent-core-demo" "-am" "-Plogback" "-DskipTests" package
```

如果需要确认运行时差异，可以分别使用 JDK17 和 JDK21+ 执行上述命令。JDK17 验证 Java 17 字节码兼容和平台线程 fallback；JDK21+ 验证虚拟线程探测、命名前缀和 Spring Boot 虚拟线程开关。
