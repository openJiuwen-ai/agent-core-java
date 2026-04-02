# com.openjiuwen.core.workflow.component.TemplateBatchProcessor

## class TemplateBatchProcessor

```java
public class TemplateBatchProcessor
```

Batch template renderer for the End component. Collects inputs from multiple data sources and renders the template once all inputs are available.

## Fields

| Signature | Description |
| --- | --- |
| `private final TemplateProcessor template` | Template. |
| `private final Map<String, Object> inputs` | Inputs. |
| `private volatile boolean rendered = false` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public TemplateBatchProcessor(TemplateProcessor template, Map<String, Object> inputs)` | Create a new `TemplateBatchProcessor` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean isRendered()` | Report whether rendered. |
| `public String render(Map<String, Object> additionalInputs, NodeSessionApi session)` | Render the template by merging the initial inputs with the additional ones. Streams through the template processor and concatenates all frame data. |
