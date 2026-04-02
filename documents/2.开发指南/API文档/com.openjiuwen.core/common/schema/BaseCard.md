# com.openjiuwen.core.common.schema.BaseCard

## class BaseCard

```java
public class BaseCard
```

`BaseCard` is the root card model used by card-like framework entities.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `id` | `String` | random UUID hex | Unique identifier generated from a UUID with hyphens removed. |
| `name` | `String` | `""` | Human-readable card name that also acts as the unique identifier within a namespace. |
| `description` | `String` | `""` | Free-form description of the card's purpose or usage. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object toolInfo()` | Extension hook for subclasses that want to expose tool-specific metadata; the base implementation returns `null`. |
| `public BaseCard copy()` | Return a shallow copy that preserves `id`, `name`, and `description`. |
| `public String toString()` | Render the card as `id=<id>,name=<name>`. |

## Notes

- Lombok annotations generate the standard getters, setters, `equals`, `hashCode`, builder API, and the no-args / all-args constructors.
