# logging

`com.openjiuwen.core.common.logging` 定义日志接口约定、全局注册中心、延迟初始化门面，以及线程级日志上下文和路径校验工具。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`defaults`](./logging/defaults.README.md) | 默认 YAML 配置、默认常量、`DefaultLogger` 实现以及全局便捷门面。 |
| [`events`](./logging/events.README.md) | 结构化事件基类、事件类型枚举、事件工厂注册表和事件脱敏工具。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`LoggerProtocol`](./logging/LoggerProtocol.md) | Java 侧统一日志接口，定义等级日志、异常日志、配置读取和重载能力。 |
| [`LazyLogger`](./logging/LazyLogger.md) | 通过 `Supplier<LoggerProtocol>` 延迟解析真实 logger 的代理实现。 |
| [`LogManager`](./logging/LogManager.md) | 维护按 `logType` 缓存的全局 logger 注册中心，并负责默认初始化。 |
| [`Loggers`](./logging/Loggers.md) | 预定义的模块级懒加载 logger 单例集合。 |
| [`LoggingUtils`](./logging/LoggingUtils.md) | 维护线程级 `sessionId/traceId`，并提供日志路径与容量校验工具。 |

## 日志框架选择

`agent-core-java` 主包只依赖 `slf4j-api`，不传递 Logback、Log4j2 或其他具体日志实现。业务应用需要自行选择一个 SLF4J 2 provider。

Logback 示例：

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.3</version>
</dependency>
```

Log4j2 示例：

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
    <version>2.23.1</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.23.1</version>
</dependency>
```

SDK 的 `level` 配置只控制 SDK 内部日志阈值。日志是否输出到控制台、文件或滚动文件，由应用侧的 `logback.xml`、`log4j2.xml` 或其他 provider 配置决定。

## 说明

- `LogManagerTest` 覆盖自定义 logger 注册、按需创建、缓存复用、`reset()` 行为，以及 `LoggingUtils` 的线程隔离与默认 `sessionId` 行为。
- `LogManager` 在未显式设置工厂时会尝试反射加载 `DefaultLogger`；未知 `logType` 会回退到 `level=INFO`、`output=console`。
