---
description: Logger selection, MDC trace-id, sanitization, and log level conventions for agent-core-java.
language: english
paths:
  - "src/main/java/com/openjiuwen/**/*.java"
  - "src/main/resources/logback.xml"
---

# Logging Rules

## Logger Selection

Use the `Loggers` facade (`com.openjiuwen.core.common.logging.Loggers`) —
never instantiate SLF4J `LoggerFactory` or `java.util.logging.Logger`
directly.

| Logger field | Use for modules |
|---|---|
| `Loggers.AGENT` | `core.singleagent`, `agentteams.*`, `dev_tools.tune`, `agentevolving`, `core.application.llm` |
| `Loggers.TOOL` | `harness.tools`, tool results in `AbilityManager` |
| `Loggers.MULTI_AGENT` | `core.multiagent`, hierarchical teams |
| `Loggers.CONTROLLER` | `core.controller`, `core.application` event handlers |
| `Loggers.SESSION` | `core.session`, stream writers, checkpointers |
| `Loggers.CONTEXT_ENGINE` | `core.context`, compressors, offloaders |
| `Loggers.GRAPH` | `core.graph`, pregel, stream actors |
| `Loggers.MEMORY` | `core.memory`, team memory, migrations |
| `Loggers.SYS_OPERATION` | `core.sysop`, shell/fs/code operations |
| `Loggers.LLM` | `core.foundation.llm`, model clients |
| `Loggers.STORE` | `core.foundation.store` |
| `Loggers.MCP` | `core.foundation.tool.mcp` |
| `Loggers.RUNNER` | `core.security.guardrail` |
| `Loggers.RETRIEVAL` | `core.retrieval.indexing` |

New module with no match → use `Loggers.COMMON`; add a new field to
`Loggers.java` if log volume is significant.

## Declaration

```java
// Pattern A: inline static field
private static final LoggerProtocol logger = Loggers.GRAPH;

// Pattern B: direct call
Loggers.AGENT.info("message: {}", value);
```

Always use `LoggerProtocol` type, never SLF4J `Logger`.

## MDC and Trace ID

MDC is managed **automatically** by `DefaultLogger` — do not call
`MDC.put` / `MDC.remove` in business code.

- Trace ID comes from `LoggingUtils.getSessionId()` (InheritableThreadLocal).
- In team scenarios, set via `SpawnContext.setSessionId(sid)` /
  `resetSessionId()` — never call `LoggingUtils.setSessionId()` directly.

## Log Levels

| Method | When |
|---|---|
| `debug` | Flow tracing; disabled in production |
| `info` | Normal lifecycle events |
| `warning` | Recoverable issues |
| `error` | Failures affecting current operation |
| `critical` | Failures requiring process restart |
| `exception` | Log a throwable — preferred over `error(msg, e)` |

## Sanitization (Automatic — Do Not Replicate)

1. **Control chars** — `DefaultLogger.sanitize()` strips code < 32 or
   == 127 from every message.
2. **Sensitive fields** — `EventSanitizer` redacts 11 fields in
   structured events: `messages`, `response_content`, `input_content`,
   `query`, `arguments`, `result`, `message_content`, `tool_calls`,
   `input_data`, `output_data`, `retrieved_memories`.

Do not log raw user input, LLM responses, tool arguments, or tool
results at `info`+ — these are only redacted in the structured event
system. Use `debug` level or keep messages generic.

## Anti-patterns

- `LoggerFactory.getLogger(...)` — bypasses LazyLogger, MDC, sanitization
- `System.out.println(...)` — no MDC, no level, no file routing
- `MDC.put("trace_id", ...)` — managed by `DefaultLogger`
- Logging full tool arguments at `info` — not redacted by `EventSanitizer`
- `logger.error(msg)` without exception — loses stack trace; use
  `logger.exception(msg, e)`
