# com.openjiuwen.core.workflow.component.TemplateUtils

## class TemplateUtils

```java
public class TemplateUtils
```

Utility class for template operations: rendering and splitting.

## Constructors

| Signature | Description |
| --- | --- |
| `private TemplateUtils()` | Create a new `TemplateUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile()` | Compile the workflow graph into an executable graph. |
| `public static String renderTemplate(String template, java.util.Map<String, Object> inputs)` | Render a template string with `{{variable`}} substitution. Uses safe substitution – missing keys are replaced with empty string. |
| `public static List<String> renderTemplateToList(String template)` | Split template into a list of segments (static text and `{{variable`}} parts). Empty segments are filtered out. |
