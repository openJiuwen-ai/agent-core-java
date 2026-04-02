# tool

`com.openjiuwen.core.foundation.tool` 提供工具抽象、工具卡片、注解扫描、本地函数封装、MCP 适配、REST API 适配，以及 JSON Schema 提取辅助类。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`annotation`](tool/annotation.README.md) | 提供把 Java 方法声明为工具定义的运行时注解。 |
| [`function`](tool/function.README.md) | 提供注解扫描工厂与本地函数工具包装器。 |
| [`mcp`](tool/mcp.README.md) | 提供 MCP 客户端协议、服务器配置、MCP 工具与工具卡片。 |
| [`schema`](tool/schema.README.md) | 提供面向模型函数调用的工具描述对象。 |
| [`service_api`](tool/service_api.README.md) | 提供把结构化输入映射到 HTTP 请求的 REST 工具实现。 |
| [`utils`](tool/utils.README.md) | 提供基于反射的调用签名与类型 Schema 提取器。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`Tool`](tool/Tool.md) | 所有工具的抽象基类，统一定义 `invoke` 与 `stream` 契约。 |
| [`ToolCard`](tool/ToolCard.md) | 工具元数据卡片，补充输入参数 Schema 与自定义属性。 |

## 关键行为

- `LocalFunction` 会在执行前按 `ToolCard.inputParams` 调用 `SchemaUtils.formatWithSchema(...)` 校验和格式化输入。
- `McpTool` 通过 `McpClient.callTool(...)` 调用远端工具，返回值统一包装为包含 `result` 的 `Map`。
- `RestfulApi` 负责路径参数替换、查询串拼接、请求头设置、JSON 请求体序列化与响应解析。
- `CallableSchemaExtractor` 与 `TypeSchemaExtractor` 会把方法参数、字段与嵌套类型转换为 JSON Schema 结构。

## 相关测试

- `ToolCardTest`
- `LocalFunctionTest`
- `McpToolTest`
- `ApiParamMapperTest`
- `RestfulApiTest`
- `ResponseParserTest`
