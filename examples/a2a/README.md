# A2A Remote Client Example

This example demonstrates how to create a Java `RemoteAgent` that talks to an A2A server over JSON-RPC.

## Usage

You need an A2A-compatible server endpoint, typically something like:

`http://host:port/a2a/jsonrpc`

Then create a `RemoteAgent` with `ProtocolEnum.A2A` and invoke it with the same input shape used by local agents.

## Example snippet

```java
RemoteAgent agent = new RemoteAgent(
    "remote-a2a-agent",
    "",
    null,
    null,
    ProtocolEnum.A2A,
    Map.of("url", "http://127.0.0.1:8080")
);

AgentResult result = (AgentResult) agent.invoke(
    Map.of("query", "hello", "conversation_id", "conv-1"),
    10.0
);
```

The client normalizes URLs that omit `/a2a/jsonrpc`.
