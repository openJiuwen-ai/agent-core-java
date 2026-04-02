# com.openjiuwen.core.workflow.component.TemplateProcessor

## class TemplateProcessor

```java
public class TemplateProcessor
```

Template streaming/rendering processor for the End component. Manages template segments, variable positions, and supports both synchronous rendering and streaming output.

## Fields

| Signature | Description |
| --- | --- |
| `private final String template` | Template. |
| `private final List<String> segments` | Segments. |
| `private final Set<Integer> variablePositions` | Variable positions. |
| `private int currentPosition` | Current position. |
| `private int chunkIndex` | Chunk index. |
| `private int dataSourceCount` | Data source count. |
| `private int count` | Count. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TemplateProcessor(String template)` | Create a new `TemplateProcessor` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setDataSourceCount(int dataSourceCount)` | Update the data source count. |
| `public int currentPosition()` | Execute `currentPosition`. |
| `public String getCurrentSegment()` | Return the current segment. |
| `private String getSegment(int pos)` | Return the segment. |
| `public boolean shouldRender()` | Execute `shouldRender`. |
| `public int advancePosition()` | Execute `advancePosition`. |
| `public String render(Map<String, Object> inputs)` | Render the entire template with the given inputs (synchronous). |
| `public void reset()` | Reset position and counters. |
| `public boolean isFinished()` | Report whether finished. |
| `public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, NodeSessionApi session)` | Render the template as a stream of frames. Each frame is a `Map` with "data" and "index" keys. In Java the iteration is synchronous via an `Iterator`. |
| `private boolean needRender(Object inputs)` | Execute `needRender`. |
