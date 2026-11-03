---
description: Jackson ObjectMapper, annotation, and serialization conventions for agent-core-java.
language: chinese
paths:
  - "src/main/java/com/openjiuwen/**/*.java"
  - "src/test/java/com/openjiuwen/**/*.java"
---

# JSON Serialization Rules

Jackson 2.17 is the only JSON library. Do not introduce Gson, org.json,
JsonB, or any other JSON library.

## ObjectMapper

### Reuse, never create per-call

`ObjectMapper` is thread-safe after configuration. Creating one per method
call wastes memory and CPU (serializer/deserializer cache rebuild).

```java
// OK — class-level singleton
private static final ObjectMapper MAPPER = new ObjectMapper();

// BAD — created per call
public String toJson() {
    return new ObjectMapper().writeValueAsString(this);  // forbidden
}
```

The shared singleton is available via `JsonUtils.getMapper()`
(`com.openjiuwen.core.common.security.JsonUtils`). Prefer it when
default configuration suffices.

### Configuration

Most ObjectMapper instances in this project use **default configuration**
(no custom features). When you need to customize:

| Need | How |
|---|---|
| Ignore unknown properties | `.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` |
| Skip null fields in output | `@JsonInclude(Include.NON_NULL)` on the class (preferred over global config) |
| Java 8 date/time | `jackson-datatype-jsr310` is on classpath; register via `.findAndRegisterModules()` or `.registerModule(new JavaTimeModule())` |

Do not change global defaults (e.g., naming strategy, serialization features)
on a shared ObjectMapper — create a dedicated instance with documented reason.

## Annotations

### Card classes

Card classes (`BaseCard`, `AgentCard`, `ToolCard`, `WorkflowCard`,
`SysOperationCard`, `GroupCard`) rely on Jackson's default bean
serialization via getters/setters. Do not add `@JsonProperty` to
fields that already follow camelCase naming.

Use `@JsonProperty` only when the JSON key differs from the Java field
name (e.g., `server_name` ↔ `serverName` in `McpToolCard`).

### Common annotations by scenario

| Annotation | When to use |
|---|---|
| `@JsonProperty("snake_key")` | JSON key differs from Java field name |
| `@JsonInclude(Include.NON_NULL)` | Omit null fields from serialized output |
| `@JsonIgnoreProperties(ignoreUnknown = true)` | Tolerate unknown fields in external API responses |
| `@JsonCreator` + `@JsonProperty` | Immutable classes / enum with custom JSON representation |
| `@JsonValue` | Enum that serializes as a single string/value |
| `@JsonDeserialize(builder = ...)` | Builder-pattern deserialization |
| `@JsonAnyGetter` / `@JsonAnySetter` | Catch-all map for extensible properties |
| `@JsonIgnore` | Exclude a field from serialization entirely |

Do not use `@JsonAutoDetect` — rely on public getters/setters or field
visibility conventions.

## Serialization Patterns

### Card → JSON

Cards are static metadata designed to cross process boundaries.
They serialize via Jackson default bean introspection (getter/setter
names → camelCase JSON keys). No manual `toMap()` / `toPayload()`
methods on new Card classes — let Jackson handle it.

### Error → JSON

`BaseError.toMap()` constructs a `Map<String, Object>` with keys
`code`, `status`, `message`, `params`, `raw_message`, `details`.
`BaseError.toJson()` converts that map to JSON string.
Do not add alternative serialization methods on error classes.

### External API responses

Use `@JsonIgnoreProperties(ignoreUnknown = true)` on DTO classes that
map external API responses. This prevents deserialization failure when
the API adds new fields.

## Date/Time Serialization

- `jackson-datatype-jsr310` is on the classpath.
- For `OffsetDateTime` / `LocalDateTime` / `Instant`, register
  `JavaTimeModule` on the ObjectMapper or use `findAndRegisterModules()`.
- The project's `MessageSerializer` uses a custom format with
  `__type__: "datetime"` markers for cross-process message payloads.
  Do not replicate this pattern in new code — use `JavaTimeModule`
  with ISO-8601 format instead.

## Anti-patterns

| Anti-pattern | Why | Fix |
|---|---|---|
| `new ObjectMapper()` inside a method | Per-call creation, no cache reuse | Class-level `static final` field |
| Multiple ObjectMapper with identical config | Wasted memory | Share via `JsonUtils.getMapper()` |
| `ObjectMapper` as instance field (non-static) | One per object instance | `private static final` |
| Manual `toMap()` + `ObjectMapper` on new classes | Boilerplate, inconsistent | Jackson annotations |
| `JsonNode` for simple POJO mapping | Over-engineering | Direct `readValue(json, MyClass.class)` |
| `JSONObject` / `JSONArray` (org.json) | Wrong library | Jackson `ObjectNode` / `ArrayNode` |
