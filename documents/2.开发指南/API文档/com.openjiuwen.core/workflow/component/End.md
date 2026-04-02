# com.openjiuwen.core.workflow.component.End

## class End

```java
public class End extends WorkflowComponent
```

Exit point component of the workflow with optional response template rendering.

## Fields

| Signature | Description |
| --- | --- |
| `private final EndConfig conf` | Conf. |
| `private final String template` | Template. |
| `private final List<String> segments` | Segments. |
| `private final List<Boolean> isVariable` | Is variable. |
| `private boolean mix = false` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public End(EndConfig conf)` | Create a new `End` instance. |
| `public End(Map<String, Object> confMap)` | Create a new `End` instance. |
| `public End()` | Create a new `End` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static final Pattern TEMPLATE_PATTERN = Pattern.compile()` | Compile the workflow graph into an executable graph. |
| `public void setMix()` | Mark this End component as mixed-mode (concurrent data sources). |
| `public boolean isMix()` | Report whether mix. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | Stream the component or workflow output. |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | Transform streamed values. |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | Collect streamed values into a final output. |
| `private static Map<String, Object> materializeStreamingInputs(Map<String, Object> inputs)` | Execute `materializeStreamingInputs`. |
| `private static OutputSchema buildTemplateFrame(int index, Object data)` | Build template frame. |
| `private Iterator<Object> templateTransformIterator(Map<String, Object> inputsMap)` | Execute `templateTransformIterator`. |
| `private Iterator<Object> outputTransformIterator(Map<String, Object> inputsMap)` | Execute `outputTransformIterator`. |
| `static String renderTemplate(String template, Map<String, Object> inputs)` | Render a template string with {{variable}} substitution. |
| `static List<String> splitTemplate(String template)` | Split template into segments (static text and {{variable}} parts). |
| `static Object getNestedValue(String path, Map<String, Object> data)` | Get a value from a nested map using a dot-separated path. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
