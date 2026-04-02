# defaults

`com.openjiuwen.core.common.logging.defaults` 提供默认日志常量、YAML 配置装载器、SLF4J/JUL 双后端实现，以及 logging 配置与全局入口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ConfigManager`](./defaults/ConfigManager.md) | 读取完整 YAML 配置并支持 `logging.level` 这类点路径访问。 |
| [`DefaultLogConstants`](./defaults/DefaultLogConstants.md) | 默认日志级别、路径、文件名、轮转参数和格式串常量。 |
| [`DefaultLogger`](./defaults/DefaultLogger.md) | 基于 SLF4J + JUL 的默认 `LoggerProtocol` 实现。 |
| [`LogConfig`](./defaults/LogConfig.md) | 解析 `logging` 段并生成 common/interface/performance 等 logger 的配置。 |
| [`LoggingDefaults`](./defaults/LoggingDefaults.md) | 暴露 `config()`、`configure()`、`logConfig()` 等全局便捷入口。 |

## 说明

- `ConfigManager` 面向完整 YAML 配置树，`LogConfig` 只关注 `logging` 段并返回按 logger 拆分后的配置映射。
- `DefaultLogger` 除普通文本日志外，还支持把 `BaseLogEvent` 转成 JSON 后按事件级别输出。
