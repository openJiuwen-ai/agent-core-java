# com.openjiuwen.extensions

`com.openjiuwen.extensions` 命名空间承载 openJiuwen 框架的可选扩展模块。每个扩展提供与外部系统或标准集成的能力，可按需引入，不影响核心运行时。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`tracer_otel`](./tracer_otel/tracer_otel.md) | OpenTelemetry 链路追踪扩展，将框架内置 Tracer 的 Agent / Workflow 事件转换为 OTel Span，支持 OTLP（HTTP / gRPC）与控制台导出 |

## 阅读指引

- 从对应模块的文档入口进入，了解配置、初始化、Handler 与 Rail 的使用方式
- 每个模块文档末尾附有 Python → Java 转换说明，便于对照原始实现
