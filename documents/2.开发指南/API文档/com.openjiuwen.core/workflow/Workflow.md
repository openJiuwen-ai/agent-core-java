# com.openjiuwen.core.workflow.Workflow

## class Workflow

```java
public class Workflow
```

Main workflow class representing a directed graph of components. Orchestrates execution of connected components, managing data flow and streaming.

## Fields

| Signature | Description |
| --- | --- |
| `private final WorkflowCard card` | Card. |
| `private final BaseWorkflow internal` | Internal. |
| `private String endCompId =` | . |
| `private boolean isStreaming = false` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public Workflow(WorkflowCard card)` | Create a new `Workflow` instance. |
| `public Workflow()` | Create a new `Workflow` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()` | Execute `ObjectMapper`. |
| `private static final ExecutorService STREAM_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor()` | Execute `newVirtualThreadPerTaskExecutor`. |
| `public WorkflowCard getCard()` | Return the card. |
| `public Workflow setStartComp( String startCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | Set the starting component of the workflow. |
| `public Workflow setStartComp(String startCompId, ComponentComposable component, Object inputsSchema)` | Compatibility overload for translated tests that omit outputs schema. |
| `public Workflow setStartComp(String startCompId, Object component, Object inputsSchema)` | Compatibility overload for translated tests that still use legacy POJO nodes. |
| `public Workflow addWorkflowComp( String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | Add a component to the workflow graph. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema)` | Simplified addWorkflowComp with just ID, component, and schemas. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema)` | Compatibility overload for translated tests that still use legacy POJO nodes. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Boolean waitForAll)` | Compatibility overload for translated tests that place wait_for_all after schemas. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Boolean waitForAll)` | Compatibility overload for translated tests that place wait_for_all after schemas. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll)` | Compatibility overload for translated tests that place wait_for_all after schemas. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll)` | Compatibility overload for translated tests that place wait_for_all after schemas. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, Object streamInputsSchema, Object streamOutputsSchema)` | Compatibility overload for translated tests that place wait_for_all after both schemas while still passing stream schemas. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, Object streamInputsSchema, Object streamOutputsSchema)` | Compatibility overload for translated tests that place wait_for_all after both schemas while still passing stream schemas. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | Compatibility overload for translated tests that still pass explicit abilities. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | Compatibility overload for translated tests that still pass explicit abilities. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | Compatibility overload for translated tests that still pass explicit abilities. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | Compatibility overload for translated tests that still pass explicit abilities. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema)` | Compatibility overload for translated tests that omit outputs schema. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema)` | Compatibility overload for translated tests that still use legacy POJO nodes. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp)` | Minimal addWorkflowComp with just ID and component. |
| `public Workflow addWorkflowComp(String compId, Object workflowComp)` | Compatibility overload for translated tests that still use legacy POJO nodes. |
| `public Workflow setEndComp( String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, String responseMode)` | Set the ending component of the workflow. |
| `public Workflow setEndComp(String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | Simplified setEndComp. |
| `public Workflow setEndComp(String endCompId, Object component, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, String responseMode)` | Compatibility overload for translated tests that still use legacy POJO nodes with explicit stream schemas and response mode. |
| `public Workflow setEndComp(String endCompId, ComponentComposable component, String responseMode, Object inputsSchema)` | Compatibility overload for translated tests that still pass `responseMode` before the input schema. |
| `public Workflow setEndComp(String endCompId, ComponentComposable component, Object inputsSchema)` | Compatibility overload for translated tests that omit outputs schema. |
| `public Workflow setEndComp(String endCompId, Object component, String responseMode, Object inputsSchema)` | Compatibility overload for translated tests that still pass `responseMode` before the input schema. |
| `public Workflow setEndComp(String endCompId, Object component, Object inputsSchema)` | Compatibility overload for translated tests that still use legacy POJO nodes. |
| `public Workflow addConnection(Object srcCompId, String targetCompId)` | Add a data connection between components. |
| `public Workflow addStreamConnection(String srcCompId, String targetCompId)` | Add a streaming connection between components. |
| `public Workflow addConditionalConnection(String srcCompId, Object router)` | Add a conditional connection with routing logic. |
| `public Workflow addConditionalConnection(String srcCompId, Function<Object, Object> router)` | Compatibility overload so translated tests can pass lambdas without explicit casts to `Object`. |
| `public WorkflowOutput invoke(Object inputs, Object session, ModelContext context, boolean isSub)` | Execute the workflow synchronously. |
| `public WorkflowOutput invoke(Object inputs, Object session, ModelContext context, boolean isSub, boolean skipInputsValidate)` | Invoke the component or workflow. |
| `public WorkflowOutput invoke(Object inputs, Object session, ModelContext context)` | Simplified invoke without sub flag. |
| `public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context, boolean isSub)` | Execute the workflow with streaming output. |
| `public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | Stream the component or workflow output. |
| `public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, boolean isSub, boolean skipInputsValidate)` | Stream the component or workflow output. |
| `public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context)` | Stream the component or workflow output. |
| `public String draw()` | Generate a Mermaid diagram of the workflow. |
| `public String draw(String title)` | Execute `draw`. |
| `public String draw(Object title, String outputFormat, Object expandSubgraph)` | Execute `draw`. |
| `public String draw(Object title, String outputFormat, Object expandSubgraph, Object enableAnimation)` | Execute `draw`. |
| `public byte[] drawBytes(Object title, String outputFormat, Object expandSubgraph)` | Generate a binary diagram of the workflow (PNG or SVG). |
| `public byte[] drawBytes(String title, String outputFormat, Object expandSubgraph)` | Execute `drawBytes`. |
| `public HasDrawable getInternalDrawable()` | Return the internal drawable. |
| `public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context)` | Invoke the component or workflow. |
| `public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context, Object config)` | Invoke the component or workflow. |
| `public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context)` | Stream the component or workflow output. |
| `public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context, Object config)` | Stream the component or workflow output. |
| `private Object executeCompiledGraph(Object inputs, BaseSession session, ModelContext context, Object config)` | Execute `executeCompiledGraph`. |
| `private RuntimeException wrapWorkflowException(Exception e)` | Execute `wrapWorkflowException`. |
| `private WorkflowSession createWorkflowSession(Object session, List<StreamMode> streamModes)` | Create workflow session. |
| `private SubWorkflowSession createSubWorkflowSession(Object session)` | Create sub workflow session. |
| `private ActorManager buildActorManager(BaseSession session, boolean subGraph)` | Build actor manager. |
| `private void closeStreamEmitter(WorkflowSession workflowSession)` | Release owned resources. |
| `private void finishStreamActorsAfterGraph(BaseSession session, Object executionResult)` | After graph execution, stream actors for consumers (e.g. End TRANSFORM) may still be blocked waiting for producers that will not run until user input is returned. When the graph yielded `PregelConstants#TASK_STATUS_INTERRUPT`, shut actors down instead of awaiting completion. |
| `private static boolean graphYieldedInterrupt(Object executionResult)` | Execute `graphYieldedInterrupt`. |
| `private List<Object> drainSubWorkflowStream(SubWorkflowSession subSession)` | Execute `drainSubWorkflowStream`. |
| `private List<Object> collectOutputChunks(WorkflowSession workflowSession)` | Collect streamed values into a final output. |
| `private Double resolveTimeoutSeconds(WorkflowSession workflowSession, String configKey)` | Execute `resolveTimeoutSeconds`. |
| `private long resolveTimeoutMillis(WorkflowSession workflowSession, String configKey)` | Execute `resolveTimeoutMillis`. |
| `private long resolveReceiveTimeoutMillis(long configuredTimeoutMs, long executionDeadlineNanos)` | Execute `resolveReceiveTimeoutMillis`. |
| `private long remainingExecutionMillis(long executionDeadlineNanos)` | Execute `remainingExecutionMillis`. |
| `private boolean isExecutionDeadlineReached(long executionDeadlineNanos)` | Report whether execution deadline reached. |
| `private String formatTimeoutSeconds(long timeoutMs)` | Execute `formatTimeoutSeconds`. |
| `private RuntimeException buildWorkflowExecutionTimeout(String timeoutText)` | Build workflow execution timeout. |
| `private <T> T executeWithWorkflowTimeout(Callable<T> task, long timeoutMs)` | Execute `executeWithWorkflowTimeout`. |
| `private List<Object> resolveInterruptedOutputChunks(Object executionResult, List<Object> outputChunks)` | Execute `resolveInterruptedOutputChunks`. |
| `private boolean isInterrupted(Object executionResult, List<Object> outputChunks)` | Report whether interrupted. |
| `private void resetGraphExecutionState()` | Execute `resetGraphExecutionState`. |
| `private void traceWorkflowStart(WorkflowSession workflowSession, Object inputs)` | Execute `traceWorkflowStart`. |
| `private void traceWorkflowDone(WorkflowSession workflowSession)` | Execute `traceWorkflowDone`. |
| `private BaseSession extractInnerSession(Object session)` | Execute `extractInnerSession`. |
| `private String normalizeTitle(Object title)` | Execute `normalizeTitle`. |
| `private boolean normalizeEnableAnimation(Object enableAnimation)` | Execute `normalizeEnableAnimation`. |
| `private int normalizeExpandSubgraph(Object expandSubgraph)` | Execute `normalizeExpandSubgraph`. |
| `private void validateSession(Object session)` | Validate session. |
| `private Object validateInputs(Object inputs, boolean skipInputsValidate)` | Validate inputs. |
| `private void validateTopLevelInputSchema(Map<String, Object> schemaMap)` | Validate top level input schema. |
| `private Map<String, Object> resolveInputSchema(Object schema)` | Execute `resolveInputSchema`. |
| `private Map<String, Object> convertInputsToMap(Object inputs)` | Execute `convertInputsToMap`. |
| `private Object resolveFinalStreamPayload(WorkflowSession workflowSession)` | Execute `resolveFinalStreamPayload`. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
