# com.openjiuwen.core.common.schema.Part

## class Part

```java
public class Part
```

`Part` is a lightweight content fragment DTO used to carry typed content plus optional metadata.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `type` | `String` | `null` | Logical part type, such as text or another caller-defined content category. |
| `content` | `String` | `null` | Serialized content for the part. |
| `metadata` | `Map<String, Object>` | `null` | Optional extra attributes associated with the part. |

## Notes

- Lombok annotations generate the getters, setters, builder API, and the no-args / all-args constructors.
