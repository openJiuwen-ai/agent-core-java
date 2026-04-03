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

## 说明

- `LogManagerTest` 覆盖自定义 logger 注册、按需创建、缓存复用、`reset()` 行为，以及 `LoggingUtils` 的线程隔离与默认 `sessionId` 行为。
- `LogManager` 在未显式设置工厂时会尝试反射加载 `DefaultLogger`；未知 `logType` 会回退到 `level=INFO`、`output=console`。
