# com.openjiuwen.core.foundation.tool.Tool

## class Tool

```java
public abstract class Tool
```

工具体系的抽象基类。它保存 `ToolCard`，并定义同步执行与流式执行两个统一入口。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `card` | `ToolCard` | `-` | 工具卡片。构造时要求非空且 `id` 非空字符串。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public ToolCard getCard()` | 返回当前工具持有的卡片对象。 |
| `public abstract Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 执行完整工具调用，具体行为由子类实现。 |
| `public Object invoke(Map<String, Object> inputs) throws Exception` | 以空 `kwargs` 调用重载版本。 |
| `public abstract Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 以流式方式执行工具，返回增量结果迭代器。 |
| `public Iterator<Object> stream(Map<String, Object> inputs) throws Exception` | 以空 `kwargs` 调用流式重载版本。 |

## 使用说明

- `Tool` 自身不实现具体业务逻辑，只负责约束所有工具共有的调用形态。
- 源码中的 `Tool(ToolCard card)` 为受保护构造器，供子类初始化时使用，因此不作为公开构造方法列出。
- Java 版本没有内建异步接口；如果需要异步执行，由调用方自行结合虚拟线程或 `CompletableFuture` 处理。

## 相关测试

- `LocalFunctionTest`
- `McpToolTest`
- `RestfulApiTest`
- `ToolCardTest`
