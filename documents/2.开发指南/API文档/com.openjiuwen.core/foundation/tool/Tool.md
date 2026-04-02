# com.openjiuwen.core.foundation.tool.Tool

## class Tool

```java
public abstract class Tool
```

Abstract base class for all tools. Defines the contract for tool invocation and streaming. Usage:

## Notes

- The constructor rejects null cards and cards without an id before a concrete tool instance can be created.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `card` | `ToolCard` | `-` | The tool configuration card. */ |

## Constructors

| Signature | Description |
| --- | --- |
| `protected Tool(ToolCard card)` | Construct a new tool with the given configuration card. |

## Methods

| Signature | Description |
| --- | --- |
| `public ToolCard getCard()` | Get the tool card. |
| `public abstract Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | Execute the tool with provided inputs and return the final result. This method performs complete tool execution in a single call. In Java, async behavior is achieved via Virtual Threads or CompletableFuture at the caller's discretion. |
| `public Object invoke(Map<String, Object> inputs) throws Exception` | Execute the tool with provided inputs (no extra kwargs). |
| `public abstract Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | Execute the tool and stream incremental results. Returns an `Iterator` to yield partial results as they become available. For reactive streaming, callers can wrap this in a Reactor Flux. |
| `public Iterator<Object> stream(Map<String, Object> inputs) throws Exception` | Execute the tool and stream incremental results (no extra kwargs). |

## Related Tests

- `LocalFunctionTest`, `McpToolTest`, `RestfulApiTest`, `ToolCardTest`
