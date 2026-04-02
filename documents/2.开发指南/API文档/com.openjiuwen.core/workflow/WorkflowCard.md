# com.openjiuwen.core.workflow.WorkflowCard

## class WorkflowCard

```java
= true) public class WorkflowCard extends BaseCard
```

Metadata card for a workflow. Contains descriptive information and input schema for a workflow.

## Fields

| Signature | Description |
| --- | --- |
| `private String version =` | . |
| `private Object inputParams` | Input params. |

## Constructors

| Signature | Description |
| --- | --- |
| `public WorkflowCard(String id, String name)` | Convenience constructor: WorkflowCard(id, name). |
| `public WorkflowCard(String id, String name, String version, String description)` | Compatibility constructor for translated tests that still pass `id, name, version, description` positionally. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object toolInfo()` | Execute `toolInfo`. |
| `public String str()` | Execute `str`. |
| `private Map<String, Object> resolveInputParamsSchema()` | Execute `resolveInputParamsSchema`. |

## Notes

- This type uses Lombok-generated members; the page lists source-defined fields and explicit methods only.
- Representative workflow regression coverage appears in `WorkflowTest.java`.
