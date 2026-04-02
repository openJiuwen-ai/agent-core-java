# utils

`com.openjiuwen.core.common.utils` contains reusable helpers for nested-map transforms, hashing, local IP discovery, context message history, JSON Schema formatting, and singleton lifecycle control.

## Core Types

| Type | Description |
| --- | --- |
| [`DictUtils`](./utils/DictUtils.md) | Flattens, extracts, formats, and rebuilds nested `Map` / `List` structures. |
| [`HashUtil`](./utils/HashUtil.md) | Generates deterministic SHA-256 cache keys from API credentials and provider metadata. |
| [`IpUtils`](./utils/IpUtils.md) | Discovers the local outbound IPv4 address with a safe loopback fallback. |
| [`MessageUtils`](./utils/MessageUtils.md) | Adds or reads chat-history messages from `ContextEngine` / `ModelContext` instances. |
| [`SchemaUtils`](./utils/SchemaUtils.md) | Fills defaults, validates map payloads against a JSON-Schema-like structure, and reflects simple schemas from classes. |
| [`SingletonSupport`](./utils/SingletonSupport.md) | Supplies thread-safe singleton creation and reset helpers for Java services. |

## Notes

- `DictUtilsTest` covers leaf extraction, flattening, path formatting, and rebuild behavior, including list-index paths.
- `SchemaUtilsTest` covers default filling, validation failures, reflective schema extraction, and null/default edge cases.
