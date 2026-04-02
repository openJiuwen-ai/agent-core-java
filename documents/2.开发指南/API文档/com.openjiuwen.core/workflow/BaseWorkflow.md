# com.openjiuwen.core.workflow.BaseWorkflow

## class BaseWorkflow

```java
public class BaseWorkflow implements HasDrawable
```

Base workflow implementation providing graph construction, edge management, component configuration, and ability inference.

## Fields

| Signature | Description |
| --- | --- |
| `private static final String WORKFLOW_DRAWABLE =` | . |
| `private final Graph graph` | Graph. |
| `private final WorkflowConfig workflowConfig` | Workflow config. |
| `private final WorkflowSpec workflowSpec` | Workflow spec. |
| `private final StreamGraph streamActor` | Stream actor. |
| `private final ProxySession session` | Session. |
| `private final Drawable drawable` | Drawable. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BaseWorkflow()` | Create a new `BaseWorkflow` instance. |
| `public BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph)` | Create a new `BaseWorkflow` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static final Pattern COMP_ID_PATTERN = Pattern.compile()` | Compile the workflow graph into an executable graph. |
| `public WorkflowConfig getConfig()` | Return the config. |
| `public Graph getGraph()` | Return the graph. |
| `public StreamGraph getStreamActor()` | Return the stream actor. |
| `public BaseWorkflow addWorkflowComp( String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | Add a workflow component with full configuration. |
| `public BaseWorkflow startComp(String startCompId)` | Execute `startComp`. |
| `public BaseWorkflow endComp(String endCompId)` | Execute `endComp`. |
| `public BaseWorkflow addConnection(Object srcCompId, String targetCompId)` | Add connection. |
| `public BaseWorkflow addStreamConnection(String srcCompId, String targetCompId)` | Add stream connection. |
| `public BaseWorkflow addConditionalConnection(String srcCompId, Object router)` | Add conditional connection. |
| `public ExecutableGraph<?, ?> compile(BaseSession sessionArg, Object context)` | Compile the workflow graph into an executable graph. |
| `public String toMermaid(String title, int expandSubgraph, boolean enableAnimation)` | Execute `toMermaid`. |
| `public String toMermaid()` | Execute `toMermaid`. |
| `public byte[] toMermaidPng(String title, int expandSubgraph)` | Render the workflow graph as a PNG image. |
| `public byte[] toMermaidPng()` | Execute `toMermaidPng`. |
| `public byte[] toMermaidSvg(String title, int expandSubgraph)` | Render the workflow graph as an SVG image. |
| `public byte[] toMermaidSvg()` | Execute `toMermaidSvg`. |
| `public Drawable getDrawable()` | Return the drawable. |
| `static boolean isDrawableEnabled()` | Report whether drawable enabled. |
| `public void autoCompleteAbilities()` | Auto-complete component abilities based on edge topology. |
| `public void reset()` | Execute `reset`. |
| `private EdgeTopology buildEdgeTopology()` | Build edge topology. |
| `private void validateEdgeNodes(EdgeTopology edgeTopology)` | Validate edge nodes. |
| `private List<String> collectProblematicEdges(EdgeTopology edgeTopology, Set<String> missingNodes)` | Collect streamed values into a final output. |
| `private void collectProblematicEdges(List<String> edgeDetails, Map<String, List<String>> edgeMap, Set<String> missingNodes, ConnectionType connectionType)` | Collect streamed values into a final output. |
| `private void completeLoopNodeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided)` | Execute `completeLoopNodeAbilities`. |
| `private void completeStreamNodeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided)` | Execute `completeStreamNodeAbilities`. |
| `private void completeInvokeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided)` | Execute `completeInvokeAbilities`. |
| `private void validateCompId(String compId)` | Validate comp id. |
| `private void validateCompAbility(String compId, List<ComponentAbility> abilities, Boolean waitForAll)` | Validate comp ability. |
| `private void validateEdge(Object srcCompId, String targetCompId, StatusCode errorCode)` | Validate edge. |
| `private void validateSchemas(String compId, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema)` | Validate schemas. |
| `private void validateSchemaOverlap(String compId, Object firstSchema, Object secondSchema, String firstName, String secondName)` | Validate schema overlap. |
| `private void addAbilityToNode(String compId, ComponentAbility ability)` | Add ability to node. |
| `private static Map<String, List<String>> invertMap(Map<String, List<String>> sourceMap)` | Execute `invertMap`. |
