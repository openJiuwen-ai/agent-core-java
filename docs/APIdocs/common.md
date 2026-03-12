# Common 模块 API 文档

> 包路径：`com.openjiuwen.core.common`

常量、异常、日志事件、通用 schema 与辅助工具集合。基于 `common` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `81` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.common.constants` | 3 |
| `com.openjiuwen.core.common.exception` | 28 |
| `com.openjiuwen.core.common.logging` | 7 |
| `com.openjiuwen.core.common.logging.defaults` | 4 |
| `com.openjiuwen.core.common.logging.events` | 23 |
| `com.openjiuwen.core.common.schema` | 4 |
| `com.openjiuwen.core.common.security` | 6 |
| `com.openjiuwen.core.common.utils` | 6 |

## `com.openjiuwen.core.common.constants`

公开类型：`3`

### `Constant`

- 类型：`class`
- 声明：`public final class Constant`
- 说明：Global constants used across the agent-core framework.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `USER_FIELDS` | `String` | `public static final` | `"userFields"` | IR userFields key |
| `QUERY` | `String` | `public static final` | `"query"` | - |
| `SYSTEM_FIELDS` | `String` | `public static final` | `"systemFields"` | IR systemFields key |
| `INTERACTION` | `String` | `public static final` | `"__interaction__"` | Workflow interaction marker |
| `INTERACTIVE_INPUT` | `String` | `public static final` | `"__interactive_input__"` | Dynamic interaction input raised by nodes |
| `INPUTS_KEY` | `String` | `public static final` | `"inputs"` | - |
| `CONFIG_KEY` | `String` | `public static final` | `"config"` | - |
| `END_FRAME` | `String` | `public static final` | `"all streaming outputs finish"` | - |
| `END_NODE_STREAM` | `String` | `public static final` | `"end node stream"` | - |
| `LOOP_ID` | `String` | `public static final` | `"__sys_loop_id"` | - |
| `INDEX` | `String` | `public static final` | `"index"` | - |
| `FINISH_INDEX` | `String` | `public static final` | `"finish_index"` | - |
| `MAX_COLLECTION_SIZE` | `int` | `public static final` | `100000` | Maximum collection size for safety |
| `MAX_EXPRESSION_LENGTH` | `int` | `public static final` | `5000` | Maximum expression length for safety |
| `MAX_AST_DEPTH` | `int` | `public static final` | `50` | Maximum AST depth for safety |
| `NESTED_LOOP_DEPTH` | `int` | `public static final` | `1` | Nested loop depth limit |

### `ControllerType`

- 类型：`enum`
- 声明：`public enum ControllerType`
- 说明：Controller type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `REACT_CONTROLLER` | `new ControllerType("react")` | - |
| `WORKFLOW_CONTROLLER` | `new ControllerType("workflow")` | - |
| `UNDEFINED` | `new ControllerType("undefined")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static ControllerType fromValue(String value)` | `ControllerType` | Parse a string value into the corresponding ControllerType. |

### `TaskType`

- 类型：`enum`
- 声明：`public enum TaskType`
- 说明：Task type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `PLUGIN` | `new TaskType("plugin")` | - |
| `WORKFLOW` | `new TaskType("workflow")` | - |
| `MCP` | `new TaskType("mcp")` | - |
| `UNDEFINED` | `new TaskType("undefined")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static TaskType fromValue(String value)` | `TaskType` | Parse a string value into the corresponding TaskType. |

## `com.openjiuwen.core.common.exception`

公开类型：`28`

### `AgentError`

- 类型：`class`
- 声明：`public class AgentError extends ExecutionError`
- 说明：Agent execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public AgentError(StatusCode status, Map<String, Object> params)` | - |
| `public AgentError(StatusCode status)` | - |

### `ApplicationError`

- 类型：`class`
- 声明：`public class ApplicationError extends ExecutionError`
- 说明：Application-level execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ApplicationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ApplicationError(StatusCode status, Map<String, Object> params)` | - |
| `public ApplicationError(StatusCode status)` | - |

### `BaseError`

- 类型：`class`
- 声明：`public class BaseError extends RuntimeException`
- 说明：Framework unified exception base class.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | Construct a new BaseError. |
| `public BaseError(StatusCode status, String msg, Object details, Throwable cause)` | Convenience constructor with builder-style params. |
| `public BaseError(StatusCode status, Map<String, Object> params)` | - |
| `public BaseError(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Standard structured output for API / RPC / logging. |
| `public StatusCode getStatus()` | `StatusCode` | - |
| `public int getCode()` | `int` | - |
| `public Map<String, Object> getParams()` | `Map<String, Object>` | - |
| `public Object getDetails()` | `Object` | - |
| `public String getTemplateMessage()` | `String` | - |
| `public String getMessage()` | `String` | - |
| `public boolean isRecoverable()` | `boolean` | - |
| `public boolean isFatal()` | `boolean` | - |
| `public String toString()` | `String` | - |
| `protected boolean defaultRecoverable()` | `boolean` | Subclasses override to define default recoverability. |
| `protected boolean defaultFatal()` | `boolean` | Subclasses override to define default fatality. |

### `ComponentError`

- 类型：`class`
- 声明：`public class ComponentError extends ExecutionError`
- 说明：Component execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ComponentError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ComponentError(StatusCode status, Map<String, Object> params)` | - |
| `public ComponentError(StatusCode status)` | - |

### `ConfigurationError`

- 类型：`class`
- 声明：`public class ConfigurationError extends FrameworkError`
- 说明：Configuration error \u2014 a specialized FrameworkError.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ConfigurationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ConfigurationError(StatusCode status, Map<String, Object> params)` | - |
| `public ConfigurationError(StatusCode status)` | - |

### `ContextError`

- 类型：`class`
- 声明：`public class ContextError extends ExecutionError`
- 说明：Context engine error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ContextError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ContextError(StatusCode status, Map<String, Object> params)` | - |
| `public ContextError(StatusCode status)` | - |

### `ErrorHelper`

- 类型：`class`
- 声明：`public final class ErrorHelper`
- 说明：Convenience factory methods for building and raising exceptions.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static BaseError buildError(StatusCode status)` | `BaseError` | Build exception instance without throwing. |
| `public static BaseError buildError(StatusCode status, String... kvPairs)` | `BaseError` | Build exception with key-value parameter pairs for template substitution. |
| `public static BaseError buildError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | `BaseError` | Build exception with custom message and details. |
| `public static void raiseError(StatusCode status)` | `void` | Unified error raising \u2014 throws immediately. |
| `public static void raiseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | `void` | Unified error raising with details \u2014 throws immediately. |
| `public static void systemError(StatusCode status)` | `void` | Raise a FrameworkError. |
| `public static void systemError(StatusCode status, Throwable cause, Map<String, Object> params)` | `void` | Raise a FrameworkError with cause. |
| `public static void validateError(StatusCode status)` | `void` | Raise a ValidationError. |
| `public static void validateError(StatusCode status, Throwable cause, Map<String, Object> params)` | `void` | Raise a ValidationError with cause. |
| `public static void terminate(StatusCode status)` | `void` | Raise a Termination. |
| `public static void terminate(StatusCode status, Map<String, Object> params)` | `void` | Raise a Termination with params. |

### `ErrorMessageTemplate`

- 类型：`record`
- 声明：`public record ErrorMessageTemplate(String template, Set<String> params)`
- 说明：Generates human-readable error message templates from structured inputs.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `template` | `String` | `private final` | `-` | - |
| `params` | `Set<String>` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ErrorMessageTemplate generate(String scope, String subject, String failureType, boolean withReason)` | `ErrorMessageTemplate` | Generate an error message template from structured inputs. |
| `public static ErrorMessageTemplate generate(String scope, String subject, String failureType)` | `ErrorMessageTemplate` | Overload with withReason defaulting to true. |

### `ExecutionError`

- 类型：`class`
- 声明：`public class ExecutionError extends BaseError`
- 说明：Execution-time errors during workflow / agent / tool execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExecutionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ExecutionError(StatusCode status, Map<String, Object> params)` | - |
| `public ExecutionError(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected boolean defaultRecoverable()` | `boolean` | - |
| `protected boolean defaultFatal()` | `boolean` | - |

### `ExternalDataError`

- 类型：`class`
- 声明：`public class ExternalDataError extends ExecutionError`
- 说明：Error caused by external data issues.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExternalDataError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ExternalDataError(StatusCode status, Map<String, Object> params)` | - |
| `public ExternalDataError(StatusCode status)` | - |

### `ExternalServiceError`

- 类型：`class`
- 声明：`public class ExternalServiceError extends ExecutionError`
- 说明：Error from an external service call.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExternalServiceError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ExternalServiceError(StatusCode status, Map<String, Object> params)` | - |
| `public ExternalServiceError(StatusCode status)` | - |

### `FrameworkError`

- 类型：`class`
- 声明：`public class FrameworkError extends BaseError`
- 说明：Infrastructure / environment / dependency failures.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public FrameworkError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public FrameworkError(StatusCode status, Map<String, Object> params)` | - |
| `public FrameworkError(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected boolean defaultRecoverable()` | `boolean` | - |
| `protected boolean defaultFatal()` | `boolean` | - |

### `GraphError`

- 类型：`class`
- 声明：`public class GraphError extends ExecutionError`
- 说明：Graph execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public GraphError(StatusCode status, Map<String, Object> params)` | - |
| `public GraphError(StatusCode status)` | - |

### `GuardrailError`

- 类型：`class`
- 声明：`public class GuardrailError extends ValidationError`
- 说明：Guardrail security check blocked error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GuardrailError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public GuardrailError(StatusCode status, Map<String, Object> params)` | - |
| `public GuardrailError(StatusCode status)` | - |

### `ModelError`

- 类型：`class`
- 声明：`public class ModelError extends ExecutionError`
- 说明：Model (LLM) error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ModelError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ModelError(StatusCode status, Map<String, Object> params)` | - |
| `public ModelError(StatusCode status)` | - |

### `RunnerError`

- 类型：`class`
- 声明：`public class RunnerError extends ExecutionError`
- 说明：Runner execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RunnerError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public RunnerError(StatusCode status, Map<String, Object> params)` | - |
| `public RunnerError(StatusCode status)` | - |

### `RunnerTermination`

- 类型：`class`
- 声明：`public class RunnerTermination extends Termination`
- 说明：Runner termination \u2014 carries a reason string.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RunnerTermination(String reason, StatusCode status, Map<String, Object> params)` | - |
| `public RunnerTermination(String reason, StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getReason()` | `String` | - |

### `SessionError`

- 类型：`class`
- 声明：`public class SessionError extends ExecutionError`
- 说明：Session error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SessionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public SessionError(StatusCode status, Map<String, Object> params)` | - |
| `public SessionError(StatusCode status)` | - |

### `StatusCode`

- 类型：`enum`
- 声明：`public enum StatusCode`
- 说明：Unified StatusCode enum for the entire framework.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `SUCCESS` | `new StatusCode(0, "success")` | - |
| `ERROR` | `new StatusCode(-1, "error")` | - |
| `WORKFLOW_COMPONENT_ID_INVALID` | `new StatusCode(100010, "the component id is invalid for component \'\'{comp_id}\'\', reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_COMPONENT_ABILITY_INVALID` | `new StatusCode(100011, "the ability is invalid for component \'\'{comp_id}\'\', ability={ability}, reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_EDGE_INVALID` | `new StatusCode(100012, "edge is invalid, reason=\'\'{reason}\'\', source=\'\'{src_cmp_id}\'\', target=\'\'{target_cmp_id}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_CONDITION_EDGE_INVALID` | `new StatusCode(100013, "condition edge is invalid, reason=\'\'{reason}\'\'. source=\'\'{src_cmp_id}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_COMPONENT_SCHEMA_INVALID` | `new StatusCode(100014, "component input/output schema is invalid for component \'\'{comp_id}\'\', reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_STREAM_EDGE_INVALID` | `new StatusCode(100015, "stream edge is invalid, reason=\'\'{reason}\'\', source=\'\'{src_cmp_id}\'\', target=\'\'{target_cmp_id}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_EXECUTE_INPUT_INVALID` | `new StatusCode(100016, "workflow execute input is invalid, inputs=\'\'{inputs}\'\', reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_EXECUTE_SESSION_INVALID` | `new StatusCode(100017, "execute session is invalid, reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_COMPILE_ERROR` | `new StatusCode(100100, "workflow compilation has error, error=\'\'{reason}\'\', workflow={workflow}")` | - |
| `WORKFLOW_EXECUTION_TIMEOUT` | `new StatusCode(100101, "workflow execution exceeded time limit of {timeout} seconds, workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_EXECUTION_ERROR` | `new StatusCode(100102, "workflow execution has error, error=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `WORKFLOW_INNER_ORCHESTRATION_ERROR` | `new StatusCode(100053, "workflow inner orchestration error, error=\'\'{reason}\'\'")` | - |
| `WORKFLOW_COMPONENT_EXECUTION_ERROR` | `new StatusCode(100054, "component \'\'{comp}\'\' execute \'\'{ability}\'\' error, reason=\'\'{reason}\'\', workflow=\'\'{workflow}\'\'")` | - |
| `COMPONENT_END_PARAM_INVALID` | `new StatusCode(100010, "component end params is invalid, error=\'\'{reason}\'\'")` | - |
| `COMPONENT_BRANCH_PARAM_INVALID` | `new StatusCode(101020, "component branch params is invalid, error=\'\'{reason}\'\'")` | - |
| `COMPONENT_BRANCH_EXECUTION_ERROR` | `new StatusCode(101021, "component branch execution error, error=\'\'{reason}\'\'")` | - |
| `EXPRESSION_SYNTAX_ERROR` | `new StatusCode(101024, "expression syntax error")` | - |
| `EXPRESSION_EVAL_ERROR` | `new StatusCode(101025, "expression evaluation error, reason: {error_msg}")` | - |
| `ARRAY_CONDITION_ERROR` | `new StatusCode(101026, "array condition error")` | - |
| `NUMBER_CONDITION_ERROR` | `new StatusCode(101027, "number condition error, reason: {error_msg}")` | - |
| `COMPONENT_LOOP_GROUP_PARAM_INVALID` | `new StatusCode(101030, "loop group params is invalid, error=\'\'{reason}\'\'")` | - |
| `COMPONENT_LOOP_SET_VAR_PARAM_INVALID` | `new StatusCode(101031, "loop set_var params invalid, error=\'\'{reason}\'\'")` | - |
| `COMPONENT_LOOP_EXECUTION_ERROR` | `new StatusCode(101040, "loop execution error, error=\'\'{reason}\'\', comp=\'\'{comp}\'\'")` | - |
| `COMPONENT_LOOP_CONDITION_EXECUTION_ERROR` | `new StatusCode(101041, "loop condition execution error, error=\'\'{reason}\'\', comp=\'\'{comp}\'\'")` | - |
| `COMPONENT_LOOP_BREAK_EXECUTION_ERROR` | `new StatusCode(101042, "loop break execution error, error=\'\'{reason}\'\', comp=\'\'{comp}\'\'")` | - |
| `COMPONENT_LOOP_SET_VAR_EXECUTION_ERROR` | `new StatusCode(101043, "loop set_var execution error, error=\'\'{reason}\'\', comp=\'\'{comp}\'\'")` | - |
| `COMPONENT_SUB_WORKFLOW_PARAM_INVALID` | `new StatusCode(101150, "component sub_workflow param is invalid, error=\'\'{reason}\'\'")` | - |
| `COMPONENT_LLM_TEMPLATE_CONFIG_ERROR` | `new StatusCode(101000, "component llm_template config error, reason: {error_msg}")` | - |
| `COMPONENT_LLM_RESPONSE_CONFIG_INVALID` | `new StatusCode(101001, "component llm_response_config is invalid, reason: {error_msg}")` | - |
| `COMPONENT_LLM_CONFIG_ERROR` | `new StatusCode(101002, "component llm config error, reason: {error_msg}")` | - |
| `COMPONENT_LLM_INVOKE_CALL_FAILED` | `new StatusCode(101003, "component llm_invoke call failed, reason: {error_msg}")` | - |
| `COMPONENT_LLM_EXECUTION_PROCESS_ERROR` | `new StatusCode(101004, "component llm_execution process error, reason: {error_msg}")` | - |
| `COMPONENT_LLM_INIT_FAILED` | `new StatusCode(101005, "component llm initialization failed, reason: {error_msg}")` | - |
| `COMPONENT_LLM_TEMPLATE_PROCESS_ERROR` | `new StatusCode(101006, "component llm_template process error, reason: {error_msg}")` | - |
| `COMPONENT_LLM_CONFIG_INVALID` | `new StatusCode(101007, "component llm_config is invalid, reason: {error_msg}")` | - |
| `COMPONENT_INTENT_DETECTION_INPUT_PARAM_ERROR` | `new StatusCode(101050, "component intent_detection_input parameter error, reason: {error_msg}")` | - |
| `COMPONENT_INTENT_DETECTION_LLM_INIT_FAILED` | `new StatusCode(101051, "component intent_detection_llm initialization failed, reason: {error_msg}")` | - |
| `COMPONENT_INTENT_DETECTION_INVOKE_CALL_FAILED` | `new StatusCode(101052, "component intent_detection_invoke call failed, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_INPUT_PARAM_ERROR` | `new StatusCode(101070, "component questioner_input parameter error, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_CONFIG_ERROR` | `new StatusCode(101071, "component questioner config error, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_INPUT_INVALID` | `new StatusCode(101072, "component questioner_input is invalid, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_STATE_INIT_FAILED` | `new StatusCode(101073, "component questioner_state initialization failed, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_RUNTIME_ERROR` | `new StatusCode(101074, "component questioner runtime error, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_INVOKE_CALL_FAILED` | `new StatusCode(101075, "component questioner_invoke call failed, reason: {error_msg}")` | - |
| `COMPONENT_QUESTIONER_EXECUTION_PROCESS_ERROR` | `new StatusCode(101076, "component questioner_execution process error, reason: {error_msg}")` | - |
| `COMPONENT_TOOL_EXECUTION_ERROR` | `new StatusCode(102000, "component tool execution error, reason: {error_msg}")` | - |
| `COMPONENT_TOOL_INPUT_PARAM_ERROR` | `new StatusCode(102001, "component tool_input parameter error, reason: {error_msg}")` | - |
| `COMPONENT_TOOL_INIT_FAILED` | `new StatusCode(102002, "component tool initialization failed, reason: {error_msg}")` | - |
| `AGENT_TOOL_NOT_FOUND` | `new StatusCode(120000, "agent tool not found, reason: {error_msg}")` | - |
| `AGENT_TOOL_EXECUTION_ERROR` | `new StatusCode(120001, "agent tool execution error, reason: {error_msg}")` | - |
| `AGENT_TASK_NOT_SUPPORT` | `new StatusCode(120002, "agent task is not supported, reason: {error_msg}")` | - |
| `AGENT_WORKFLOW_EXECUTION_ERROR` | `new StatusCode(120003, "agent workflow execution error, reason: {error_msg}")` | - |
| `AGENT_PROMPT_PARAM_ERROR` | `new StatusCode(120004, "agent prompt parameter error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_INVOKE_CALL_FAILED` | `new StatusCode(123000, "agent controller_invoke call failed, reason: {error_msg}")` | - |
| `AGENT_SUB_TASK_TYPE_NOT_SUPPORT` | `new StatusCode(123001, "agent sub_task_type is not supported, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_USER_INPUT_PROCESS_ERROR` | `new StatusCode(123002, "agent controller_user_input process error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_RUNTIME_ERROR` | `new StatusCode(123003, "agent controller runtime error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_EXECUTION_CALL_FAILED` | `new StatusCode(123004, "agent controller_execution call failed, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_TOOL_EXECUTION_PROCESS_ERROR` | `new StatusCode(123005, "agent controller_tool_execution process error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_TASK_PARAM_ERROR` | `new StatusCode(123006, "controller task parameter error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_INTENT_PARAM_ERROR` | `new StatusCode(123007, "controller intention parameter error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_TASK_EXECUTION_ERROR` | `new StatusCode(123008, "controller task execution error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_EVENT_HANDLER_ERROR` | `new StatusCode(123009, "controller event handler error, reason: {error_msg}")` | - |
| `AGENT_CONTROLLER_EVENT_QUEUE_ERROR` | `new StatusCode(123010, "agent controller event queue execution error, reason: {error_msg}")` | - |
| `RUNNER_TERMINATION_ERROR` | `new StatusCode(110002, "runner is already terminate")` | - |
| `RUNNER_RUN_AGENT_ERROR` | `new StatusCode(110022, "runner run agent \'\'{agent}\'\' failed, error=\'\'{reason}\'\'")` | - |
| `REMOTE_AGENT_EXECUTION_TIMEOUT` | `new StatusCode(110100, "remote agent \'\'{agent_id}\'\' execute exceed {timeout} seconds")` | - |
| `REMOTE_AGENT_EXECUTION_ERROR` | `new StatusCode(110101, "remote agent \'\'{agent_id}\'\' execute error, error=\'\'{reason}\'\'")` | - |
| `REMOTE_AGENT_RESPONSE_PROCESS_ERROR` | `new StatusCode(110102, "remote agent request process error, message_id=\'\'{message_id}\'\', process_id=\'\'{process_id}\'\', response=\'\'{code={error_code}\'\', msg=\'\'{error_msg}\'\'")` | - |
| `MESSAGE_QUEUE_INITIATION_ERROR` | `new StatusCode(110200, "init type \'\'{type}\'\' message queue error, error=\'\'{reason}\'\'")` | - |
| `MESSAGE_QUEUE_TOPIC_SUBSCRIPTION_ERROR` | `new StatusCode(110210, "subscribe topic error, topic=\'\'{topic}\'\', error=\'\'{reason}\'\'")` | - |
| `MESSAGE_QUEUE_TOPIC_MESSAGE_PRODUCTION_ERROR` | `new StatusCode(110211, "produce message error, topic=\'\'{topic}\'\', message=\'\'{message}\'\', error=\'\'{reason}\'\'")` | - |
| `MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR` | `new StatusCode(110212, "consume message error, error=\'\'{reason}\'\'")` | - |
| `MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR` | `new StatusCode(110213, "process message error, error=\'\'{reason}\'\'")` | - |
| `DIST_MESSAGE_QUEUE_CLIENT_START_ERROR` | `new StatusCode(110300, "distribute message queue client start error, error=\'\'{reason}\'\'")` | - |
| `RESOURCE_ID_VALUE_INVALID` | `new StatusCode(110400, "{resource_type} id is invalid, reason=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_VALUE_INVALID` | `new StatusCode(110401, "tag is invalid, tag={tag}, reason=\'\'{reason}\'\'")` | - |
| `RESOURCE_CARD_VALUE_INVALID` | `new StatusCode(110402, "{resource_type} card is invalid, reason=\'\'{reason}\'\'")` | - |
| `RESOURCE_PROVIDER_INVALID` | `new StatusCode(110403, "{resource_type} provider is invalid, reason=\'\'{reason}\'\'")` | - |
| `RESOURCE_VALUE_INVALID` | `new StatusCode(110404, "{resource_type} value is invalid, reason=\'\'{reason}\'\'")` | - |
| `RESOURCE_ADD_ERROR` | `new StatusCode(110430, "resource add failed, card=\'\'{card}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_REMOVE_TAG_ERROR` | `new StatusCode(110480, "tag is invalid, tag=\'\'{tag}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_ADD_RESOURCE_TAG_ERROR` | `new StatusCode(110481, "add tag failed, resource_id=\'\'{resource_id}\'\', tag=\'\'{tag}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_REMOVE_RESOURCE_TAG_ERROR` | `new StatusCode(110482, "remove resource tag failed, resource_id=\'\'{resource_id}\'\', tags=\'\'{tags}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR` | `new StatusCode(110483, "replace resource tag failed, resource_id=\'\'{resource_id}\'\', tags=\'\'{tags}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_TAG_FIND_RESOURCE_ERROR` | `new StatusCode(110484, "replace resource tag failed, resource_id=\'\'{resource_id}\'\', tags=\'\'{tags}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_SERVER_PARAM_INVALID` | `new StatusCode(110510, "server param is invalid, server_config=\'\'{server_config}\'\', error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_SERVER_CONNECTION_ERROR` | `new StatusCode(110511, "mcp server connect failed, server_config={server_config}, error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_SERVER_ADD_ERROR` | `new StatusCode(110512, "mcp server add failed, server_config={server_config}, error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_SERVER_REFRESH_ERROR` | `new StatusCode(110513, "mcp server refresh failed, server_id={server_id}, error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_SERVER_REMOVE_ERROR` | `new StatusCode(110514, "mcp server remove failed, server_id={server_id}, error=\'\'{reason}\'\'")` | - |
| `RESOURCE_MCP_TOOL_GET_ERROR` | `new StatusCode(110515, "mcp server tool get failed, server_id={server_id}, error=\'\'{reason}\'\'")` | - |
| `COMP_SESSION_INTERACT_ERROR` | `new StatusCode(111005, "interact is not support, error=\'\'{reason}\'\', comp_id={comp_id}, workflow={workflow}")` | - |
| `INTERACTION_INPUT_INVALID` | `new StatusCode(111110, "interaction input is invalid, reason={reason}")` | - |
| `CHECKPOINTER_POST_WORKFLOW_EXECUTION_ERROR` | `new StatusCode(111120, "post workflow execute error, session_id={session_id}, workflow={workflow}, error=\'\'{reason}\'\'")` | - |
| `CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR` | `new StatusCode(111121, "pre workflow execute error, session_id={session_id}, workflow={workflow}, error=\'\'{reason}\'\'")` | - |
| `CHECKPOINTER_INTERRUPT_AGENT_ERROR` | `new StatusCode(111122, "interrupt agent execute error, session_id={session_id}, agent={agent}, error=\'\'{reason}\'\'")` | - |
| `CHECKPOINTER_POST_AGENT_EXECUTION_ERROR` | `new StatusCode(111123, "post agent execute error, session_id={session_id}, agent={agent}, error=\'\'{reason}\'\'")` | - |
| `CHECKPOINTER_CONFIG_ERROR` | `new StatusCode(111124, "checkpointer config error, session_id={session_id}, error=\'\'{reason}\'\'")` | - |
| `STREAM_WRITER_MANAGER_ADD_WRITER_ERROR` | `new StatusCode(111130, "add new stream writer error, mode={mode}, error=\'\'{reason}\'\'")` | - |
| `STREAM_WRITER_MANAGER_REMOVE_WRITER_ERROR` | `new StatusCode(111131, "remove stream writer error, mode={mode}, error=\'\'{reason}\'\'")` | - |
| `STREAM_WRITER_WRITE_STREAM_VALIDATION_ERROR` | `new StatusCode(111132, "writer stream data validate error, stream_type={schema_type}, stream_data={stream_data}, error=\'\'{reason}\'\'")` | - |
| `STREAM_WRITER_WRITE_STREAM_ERROR` | `new StatusCode(111133, "writer stream data error, stream_data={stream_data}, error=\'\'{reason}\'\'")` | - |
| `STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT` | `new StatusCode(111134, "stream output first stream chunk timeout, timeout={timeout}s, error=\'\'{reason}\'\'")` | - |
| `STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT` | `new StatusCode(111135, "stream output next stream chunk timeout, interval_timeout={timeout}s, error=\'\'{reason}\'\'")` | - |
| `TRACER_WORKFLOW_TRACE_ERROR` | `new StatusCode(111140, "trace workflow error, error=\'\'{reason}\'\'")` | - |
| `TRACER_AGENT_TRACE_ERROR` | `new StatusCode(111141, "trace agent error, error=\'\'{reason}\'\'")` | - |
| `GRAPH_STATE_COMMIT_ERROR` | `new StatusCode(112030, "graph commit state error, error=\'\'{reason}\'\'")` | - |
| `DRAWABLE_GRAPH_START_NODE_INVALID` | `new StatusCode(112020, "drawable_graph start node is invalid, node={node_id}, reason={reason}")` | - |
| `DRAWABLE_GRAPH_END_NODE_INVALID` | `new StatusCode(112021, "drawable_graph end node is invalid, node={node_id}, reason={reason}")` | - |
| `DRAWABLE_GRAPH_BREAK_NODE_INVALID` | `new StatusCode(112022, "drawable_graph break node is invalid, node={node_id}, reason={reason}")` | - |
| `DRAWABLE_GRAPH_TO_MERMAID_INVALID` | `new StatusCode(112043, "drawable_graph to_mermaid error, reason={reason}")` | - |
| `GRAPH_STREAM_ACTOR_EXECUTION_ERROR` | `new StatusCode(112030, "actor manager execute error, error=\'\'{reason}\'\'")` | - |
| `GRAPH_VERTEX_EXECUTION_ERROR` | `new StatusCode(112050, "vertex execute error, error=\'\'{reason}\'\', node_id={node_id}")` | - |
| `GRAPH_VERTEX_STREAM_CALL_TIMEOUT` | `new StatusCode(112051, "vertex stream timeout, timeout={timeout}, node_id={node_id}")` | - |
| `GRAPH_VERTEX_STREAM_CALL_ERROR` | `new StatusCode(112052, "vertex stream call error, error=\'\'{reason}\'\', node_id={node_id}")` | - |
| `PREGEL_GRAPH_NODE_ID_INVALID` | `new StatusCode(112100, "node id is invalid, node_id={node_id}, error=\'\'{reason}\'\'")` | - |
| `PREGEL_GRAPH_NODE_INVALID` | `new StatusCode(112101, "node is invalid, node_id={node_id}, error=\'\'{reason}\'\'")` | - |
| `PREGEL_GRAPH_EDGE_INVALID` | `new StatusCode(112102, "edge is invalid, source_id={source_id}, target_id={target_id}, error=\'\'{reason}\'\'")` | - |
| `PREGEL_GRAPH_CONDITION_EDGE_INVALID` | `new StatusCode(112103, "condition edge is invalid, source_id={source_id}, error=\'\'{reason}\'\'")` | - |
| `AGENT_GROUP_ADD_RUNTIME_ERROR` | `new StatusCode(132000, "agent group_add runtime error, reason: {error_msg}")` | - |
| `AGENT_GROUP_CREATE_RUNTIME_ERROR` | `new StatusCode(132001, "agent group_create runtime error, reason: {error_msg}")` | - |
| `AGENT_GROUP_EXECUTION_ERROR` | `new StatusCode(132002, "agent group execution error, reason: {error_msg}")` | - |
| `CONTEXT_MESSAGE_PROCESS_ERROR` | `new StatusCode(153000, "context message process error, reason: {error_msg}")` | - |
| `CONTEXT_EXECUTION_ERROR` | `new StatusCode(153001, "context execution execution error, reason: {error_msg}")` | - |
| `CONTEXT_MESSAGE_INVALID` | `new StatusCode(153003, "context message is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_INPUT_INVALID` | `new StatusCode(155000, "retrieval embedding_input is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_MODEL_NOT_FOUND` | `new StatusCode(155001, "retrieval embedding_model not found, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_CALL_FAILED` | `new StatusCode(155002, "retrieval embedding call failed, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_RESPONSE_INVALID` | `new StatusCode(155003, "retrieval embedding_response is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED` | `new StatusCode(155004, "retrieval embedding_request call failed, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED` | `new StatusCode(155005, "retrieval embedding call failed, reason: {error_msg}")` | - |
| `RETRIEVAL_EMBEDDING_CALLBACK_INVALID` | `new StatusCode(155006, "retrieval embedding_callback is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID` | `new StatusCode(155100, "retrieval indexing_chunk_size is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID` | `new StatusCode(155101, "retrieval indexing_chunk_overlap is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_TOKENIZER_PROCESS_ERROR` | `new StatusCode(155102, "retrieval indexing_tokenizer process error, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_FILE_NOT_FOUND` | `new StatusCode(155103, "retrieval indexing_file not found, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT` | `new StatusCode(155104, "retrieval indexing_format is not supported, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND` | `new StatusCode(155105, "retrieval indexing_embed_model not found, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_DIMENSION_NOT_FOUND` | `new StatusCode(155106, "retrieval indexing_dimension not found, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_PATH_NOT_FOUND` | `new StatusCode(155107, "retrieval indexing_path not found, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR` | `new StatusCode(155108, "retrieval indexing_add_doc runtime error, reason: {error_msg}")` | - |
| `RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID` | `new StatusCode(155109, "retrieval indexing_vector_field is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT` | `new StatusCode(155200, "retrieval retriever_mode is not supported, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID` | `new StatusCode(155201, "retrieval retriever_score_threshold is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND` | `new StatusCode(155202, "retrieval retriever_embed_model not found, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_INDEX_TYPE_NOT_SUPPORT` | `new StatusCode(155203, "retrieval retriever_index_type is not supported, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_MODE_INVALID` | `new StatusCode(155204, "retrieval retriever_mode is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_CAPABILITY_NOT_SUPPORT` | `new StatusCode(155205, "retrieval retriever_capability is not supported, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_VECTOR_STORE_NOT_FOUND` | `new StatusCode(155206, "retrieval retriever_vector_store not found, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_COLLECTION_NOT_FOUND` | `new StatusCode(155207, "retrieval retriever_collection not found, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_GRAPH_RETRIEVER_NOT_FOUND` | `new StatusCode(155208, "retrieval retriever_graph_retriever not found, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND` | `new StatusCode(155209, "retrieval retriever_llm_client not found, reason: {error_msg}")` | - |
| `RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND` | `new StatusCode(155210, "retrieval retriever_top_k not found, reason: {error_msg}")` | - |
| `RETRIEVAL_UTILS_CONFIG_FILE_NOT_FOUND` | `new StatusCode(155300, "retrieval utils_config_file not found, reason: {error_msg}")` | - |
| `RETRIEVAL_UTILS_PYYAML_NOT_FOUND` | `new StatusCode(155301, "retrieval utils_pyyaml not found, reason: {error_msg}")` | - |
| `RETRIEVAL_UTILS_CONFIG_FORMAT_NOT_SUPPORT` | `new StatusCode(155302, "retrieval utils_config_format is not supported, reason: {error_msg}")` | - |
| `RETRIEVAL_UTILS_CONFIG_NOT_FOUND` | `new StatusCode(155303, "retrieval utils_config not found, reason: {error_msg}")` | - |
| `RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR` | `new StatusCode(155304, "retrieval utils_config process error, reason: {error_msg}")` | - |
| `RETRIEVAL_VECTOR_STORE_PATH_NOT_FOUND` | `new StatusCode(155400, "retrieval vector_store_path not found, reason: {error_msg}")` | - |
| `RETRIEVAL_VECTOR_STORE_QUERY_INVALID` | `new StatusCode(155400, "retrieval vector_store_query not valid, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_PARSER_NOT_FOUND` | `new StatusCode(155500, "retrieval kb_parser not found, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_CHUNKER_NOT_FOUND` | `new StatusCode(155501, "retrieval kb_chunker not found, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND` | `new StatusCode(155502, "retrieval kb_index_manager not found, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND` | `new StatusCode(155503, "retrieval kb_vector_store not found, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_INDEX_BUILD_EXECUTION_ERROR` | `new StatusCode(155504, "retrieval kb_index_build execution error, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_CHUNK_INDEX_BUILD_EXECUTION_ERROR` | `new StatusCode(155505, "retrieval kb_chunk_index_build execution error, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_TRIPLE_INDEX_BUILD_EXECUTION_ERROR` | `new StatusCode(155506, "retrieval kb_triple_index_build execution error, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR` | `new StatusCode(155507, "retrieval kb_triple_extraction process error, reason: {error_msg}")` | - |
| `RETRIEVAL_KB_DATABASE_CONFIG_INVALID` | `new StatusCode(155508, "retrieval kb_database_config is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_RERANKER_REQUEST_CALL_FAILED` | `new StatusCode(155600, "retrieval reranker_request call failed, reason: {error_msg}")` | - |
| `RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED` | `new StatusCode(155601, "retrieval reranker call failed, reason: {error_msg}")` | - |
| `RETRIEVAL_RERANKER_INPUT_INVALID` | `new StatusCode(155602, "retrieval reranker_input is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_QUERY_REWRITER_INPUT_INVALID` | `new StatusCode(155603, "retrieval query_rewriter_input is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED` | `new StatusCode(155604, "retrieval query_rewriter_llm invoke failed, reason: {error_msg}")` | - |
| `RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID` | `new StatusCode(155605, "retrieval query_rewriter_output is invalid, reason: {error_msg}")` | - |
| `RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND` | `new StatusCode(155606, "retrieval query_rewriter_prompt not found, reason: {error_msg}")` | - |
| `MEMORY_REGISTER_STORE_EXECUTION_ERROR` | `new StatusCode(158000, "failed to register {store_type} to memory engine, reason: {error_msg}")` | - |
| `MEMORY_SET_CONFIG_EXECUTION_ERROR` | `new StatusCode(158001, "failed to set {config_type} config, reason: {error_msg}")` | - |
| `MEMORY_ADD_MEMORY_EXECUTION_ERROR` | `new StatusCode(158002, "failed to add {memory_type} memory, reason: {error_msg}")` | - |
| `MEMORY_DELETE_MEMORY_EXECUTION_ERROR` | `new StatusCode(158003, "failed to delete {memory_type} memory, reason: {error_msg}")` | - |
| `MEMORY_UPDATE_MEMORY_EXECUTION_ERROR` | `new StatusCode(158004, "failed to update {memory_type} memory, reason: {error_msg}")` | - |
| `MEMORY_GET_MEMORY_EXECUTION_ERROR` | `new StatusCode(158005, "failed to get {memory_type} memory, reason: {error_msg}")` | - |
| `MEMORY_STORE_INIT_FAILED` | `new StatusCode(158006, "failed to init {store_type}, reason: {error_msg}")` | - |
| `MEMORY_CONNECT_STORE_EXECUTION_ERROR` | `new StatusCode(158007, "failed to connect {store_type}, reason: {error_msg}")` | - |
| `MEMORY_STORE_VALIDATION_INVALID` | `new StatusCode(158008, "{store_type} validation failed, reason: {error_msg}")` | - |
| `MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR` | `new StatusCode(158009, "memory migration failed, reason: {error_msg}")` | - |
| `MEMORY_REGISTER_OPERATION_VALIDATION_INVALID` | `new StatusCode(158010, "failed to register operation for entity {entity_key} with schema_version {schema_version}, reason: {error_msg}")` | - |
| `MEMORY_INIT_ERROR` | `new StatusCode(158011, "memory initialization failed, reason: {error_msg}")` | - |
| `TOOLCHAIN_AGENT_PARAM_ERROR` | `new StatusCode(170000, "toolchain agent parameter error, reason: {error_msg}")` | - |
| `TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR` | `new StatusCode(170001, "toolchain optimizer_backword execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_OPTIMIZER_UPDATE_EXECUTION_ERROR` | `new StatusCode(170002, "toolchain optimizer_update execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_OPTIMIZER_PARAM_ERROR` | `new StatusCode(170003, "toolchain optimizer parameter error, reason: {error_msg}")` | - |
| `TOOLCHAIN_EVALUATOR_EXECUTION_ERROR` | `new StatusCode(170004, "toolchain evaluator execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_TRAINER_EXECUTION_ERROR` | `new StatusCode(170005, "toolchain trainer execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR` | `new StatusCode(173000, "toolchain meta_template execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR` | `new StatusCode(173001, "toolchain feedback_template execution error, reason: {error_msg}")` | - |
| `TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR` | `new StatusCode(173002, "toolchain bad_case_template execution error, reason: {error_msg}")` | - |
| `PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED` | `new StatusCode(180000, "prompt assembler_variable initialization failed, reason: {error_msg}")` | - |
| `PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR` | `new StatusCode(180001, "prompt assembler_template parameter error, reason: {error_msg}")` | - |
| `PROMPT_TEMPLATE_RUNTIME_ERROR` | `new StatusCode(180002, "prompt template runtime error, reason: {error_msg}")` | - |
| `PROMPT_TEMPLATE_NOT_FOUND` | `new StatusCode(180003, "prompt template not found, reason: {error_msg}")` | - |
| `PROMPT_TEMPLATE_INVALID` | `new StatusCode(180004, "prompt template is invalid, reason: {error_msg}")` | - |
| `MODEL_PROVIDER_INVALID` | `new StatusCode(181000, "model provider is invalid, reason: {error_msg}")` | - |
| `MODEL_CALL_FAILED` | `new StatusCode(181001, "model call failed, reason: {error_msg}")` | - |
| `MODEL_SERVICE_CONFIG_ERROR` | `new StatusCode(181002, "model service config error, reason: {error_msg}")` | - |
| `MODEL_CONFIG_ERROR` | `new StatusCode(181003, "model config error, reason: {error_msg}")` | - |
| `MODEL_INVOKE_PARAM_ERROR` | `new StatusCode(181004, "model invoke parameter error, reason: {error_msg}")` | - |
| `MODEL_CLIENT_CONFIG_INVALID` | `new StatusCode(181005, "model client_config is invalid, reason: {error_msg}")` | - |
| `TOOL_CARD_INVALID` | `new StatusCode(182000, "card is invalid, card={card}, error=\'\'{reason}\'\'")` | - |
| `TOOL_STREAM_NOT_SUPPORTED` | `new StatusCode(182010, "stream is not support, card={card}")` | - |
| `TOOL_INVOKE_NOT_SUPPORTED` | `new StatusCode(182011, "invoke is not support, card={card}")` | - |
| `TOOL_EXECUTION_ERROR` | `new StatusCode(182012, "tool execution error, tool card={card}, reason={reason}")` | - |
| `TOOL_RESTFUL_API_CARD_CONFIG_INVALID` | `new StatusCode(182100, "config failed, {reason}")` | - |
| `TOOL_RESTFUL_API_EXECUTION_TIMEOUT` | `new StatusCode(182101, "execute {method} failed, request is timeout, timeout={timeout}s, card=[{card}]")` | - |
| `TOOL_RESTFUL_API_RESPONSE_SIZE_EXCEED_LIMIT` | `new StatusCode(182102, "execute {method} failed, response is too big, max_size={max_length}b, actual={actual_length}b, card=[{card}]")` | - |
| `TOOL_RESTFUL_API_RESPONSE_ERROR` | `new StatusCode(182103, "execute {method} failed, response error, code={code}, error=\'\'{reason}\'\'")` | - |
| `TOOL_RESTFUL_API_EXECUTION_ERROR` | `new StatusCode(182104, "RestfulApi execute {method} failed, error=\'\'{reason}\'\', card=[{card}]")` | - |
| `TOOL_RESTFUL_API_RESPONSE_PROCESS_ERROR` | `new StatusCode(182105, "RestfulApi parse response failed, error=\'\'{reason}\'\', card=[{card}]")` | - |
| `TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED` | `new StatusCode(182200, "func is not supported, card={card}")` | - |
| `TOOL_LOCAL_FUNCTION_EXECUTION_ERROR` | `new StatusCode(182205, "execute {method} failed, error=\'\'{reason}\'\', card={card}")` | - |
| `TOOL_MCP_CLIENT_NOT_SUPPORTED` | `new StatusCode(182300, "mcp client is not supported, card={card}")` | - |
| `TOOL_MCP_EXECUTION_ERROR` | `new StatusCode(182301, "execute {method} failed, error=\'\'{reason}\'\', card={card}")` | - |
| `TOOL_OPENAPI_CLIENT_EXECUTION_ERROR` | `new StatusCode(182400, "openapi client execute error, error=\'\'{reason}\'\'")` | - |
| `COMMON_LOG_PATH_INVALID` | `new StatusCode(183000, "common log_path is invalid, reason: {error_msg}")` | - |
| `COMMON_LOG_PATH_INIT_FAILED` | `new StatusCode(183001, "common log_path initialization failed, reason: {error_msg}")` | - |
| `COMMON_LOG_CONFIG_PROCESS_ERROR` | `new StatusCode(183002, "common log_config process error, reason: {error_msg}")` | - |
| `COMMON_LOG_CONFIG_INVALID` | `new StatusCode(183003, "common log_config is invalid, reason: {error_msg}")` | - |
| `COMMON_LOG_EXECUTION_RUNTIME_ERROR` | `new StatusCode(183004, "common log_execution runtime error, reason: {error_msg}")` | - |
| `STORE_VECTOR_SCHEMA_INVALID` | `new StatusCode(186000, "store vector_schema is invalid, reason: {error_msg}")` | - |
| `STORE_VECTOR_DOC_INVALID` | `new StatusCode(186001, "store vector_doc is invalid, reason: {error_msg}")` | - |
| `STORE_VECTOR_COLLECTION_NOT_FOUND` | `new StatusCode(186002, "store vector_collection not found, collection_name={collection_name}")` | - |
| `COMMON_SSL_CONTEXT_INIT_FAILED` | `new StatusCode(188000, "common ssl_context initialization failed, reason: {error_msg}")` | - |
| `COMMON_USER_CONFIG_PROCESS_ERROR` | `new StatusCode(188001, "common user_config process error, reason: {error_msg}")` | - |
| `COMMON_JSON_INPUT_PROCESS_ERROR` | `new StatusCode(188002, "common json_input process error, reason: {error_msg}")` | - |
| `COMMON_JSON_EXECUTION_PROCESS_ERROR` | `new StatusCode(188003, "common json_execution process error, reason: {error_msg}")` | - |
| `COMMON_URL_INPUT_INVALID` | `new StatusCode(188004, "common url_input is invalid, reason: {error_msg}")` | - |
| `COMMON_SSL_CERT_INVALID` | `new StatusCode(188005, "common ssl_cert is invalid, reason: {error_msg}")` | - |
| `SCHEMA_VALIDATE_INVALID` | `new StatusCode(189001, "validate data with schema failed, error=\'\'{reason}\'\', data={data}")` | - |
| `SCHEMA_FORMAT_INVALID` | `new StatusCode(189002, "format data with schema failed, error=\'\'{reason}\'\', data={data}")` | - |
| `GUARDRAIL_BLOCKED` | `new StatusCode(190000, "guardrail blocked: risk_type=\'\'{risk_type}\'\', risk_level=\'\'{risk_level}\'\', event=\'\'{event}\'\'")` | - |
| `SYS_OPERATION_MANAGER_PROCESS_ERROR` | `new StatusCode(199001, "sys operation manager process error, process: {process}, reason: {error_msg}")` | - |
| `SYS_OPERATION_CARD_PARAM_ERROR` | `new StatusCode(199002, "sys operation card param error, reason: {error_msg}")` | - |
| `SYS_OPERATION_FS_EXECUTION_ERROR` | `new StatusCode(199003, "file system operation execution error, execution: {execution}, reason: {error_msg}")` | - |
| `SYS_OPERATION_SHELL_EXECUTION_ERROR` | `new StatusCode(199004, "shell operation execution error, execution: {execution}, reason: {error_msg}")` | - |
| `SYS_OPERATION_CODE_EXECUTION_ERROR` | `new StatusCode(199005, "code operation execution error, execution: {execution}, reason: {error_msg}")` | - |
| `SYS_OPERATION_REGISTRY_ERROR` | `new StatusCode(199006, "sys operation registry error, process: {process}, reason: {error_msg}")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getCode()` | `int` | Return the integer error code. |
| `public String getErrmsg()` | `String` | Return the error message template (unformatted). |

### `StatusCodeSpec`

- 类型：`record`
- 声明：`public record StatusCodeSpec(String name, int code, String message)`
- 说明：A fully-specified status code entry generated from a StatusCodeTemplate.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `code` | `int` | `private final` | `-` | - |
| `message` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static StatusCodeSpec fromTemplate(StatusCodeTemplate template, int code)` | `StatusCodeSpec` | Create a spec by combining a template with a concrete code value. |
| `public String renderEnumMember()` | `String` | Render as a Java enum member declaration string (for codegen). |

### `StatusCodeTemplate`

- 类型：`record`
- 声明：`public record StatusCodeTemplate(String name, String codeSuggestion, String messageTemplate, String exceptionSemantic)`
- 说明：Template for generating new StatusCode entries.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `codeSuggestion` | `String` | `private final` | `-` | - |
| `messageTemplate` | `String` | `private final` | `-` | - |
| `exceptionSemantic` | `String` | `private final` | `-` | - |
| `ALLOWED_SCOPES` | `Set<String>` | `public static final` | `Set.of("WORKFLOW", "COMPONENT", "AGENT", "TOOL", "MODEL", "SESSION", "GRAPH", "CONTROLLER", "RUNNER", "PROMPT", "COMMON", "CONTEXT", "TOOLCHAIN", "MEMORY", "RETRIEVAL", "SYS_OPERATION")` | - |
| `ALLOWED_FAILURE_TYPES` | `Set<String>` | `public static final` | `Set.of("INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR", "TYPE_ERROR", "INIT_FAILED", "CALL_FAILED", "EXECUTION_ERROR", "RUNTIME_ERROR", "PROCESS_ERROR", "TIMEOUT", "INTERRUPTED")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static StatusCodeTemplate generate(String scope, String subject, String failureType, String detail)` | `StatusCodeTemplate` | Generate a StatusCodeTemplate from structured inputs. |
| `public static StatusCodeTemplate generate(String scope, String subject, String failureType)` | `StatusCodeTemplate` | - |

### `StatusMapping`

- 类型：`class`
- 声明：`public final class StatusMapping`
- 说明：Resolves which exception class to instantiate for a given StatusCode.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Function<StatusCode, BaseError> resolveExceptionFactory(StatusCode status)` | `Function<StatusCode, BaseError>` | Resolve the concrete exception class (as a factory) for the given status code. |
| `public static BaseError resolveException(StatusCode status)` | `BaseError` | Build an exception for the given status code using resolution rules. |
| `public static Map<StatusCode, Function<StatusCode, BaseError>> buildStatusExceptionMap()` | `Map<StatusCode, Function<StatusCode, BaseError>>` | Generate full StatusCode \u2192 exception factory mapping for all status codes. |

### `SysOperationError`

- 类型：`class`
- 声明：`public class SysOperationError extends ExecutionError`
- 说明：System operation error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SysOperationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public SysOperationError(StatusCode status, Map<String, Object> params)` | - |
| `public SysOperationError(StatusCode status)` | - |

### `Termination`

- 类型：`class`
- 声明：`public class Termination extends BaseError`
- 说明：Non-error control-flow termination.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Termination(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public Termination(StatusCode status, Map<String, Object> params)` | - |
| `public Termination(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected boolean defaultRecoverable()` | `boolean` | - |
| `protected boolean defaultFatal()` | `boolean` | - |

### `ToolError`

- 类型：`class`
- 声明：`public class ToolError extends ExecutionError`
- 说明：Tool execution error \u2014 may carry a BaseCard reference.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolError(StatusCode status, String msg, Object details, Throwable cause, BaseCard card, Map<String, Object> params)` | - |
| `public ToolError(StatusCode status, Map<String, Object> params)` | - |
| `public ToolError(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseCard getCard()` | `BaseCard` | - |

### `ToolchainError`

- 类型：`class`
- 声明：`public class ToolchainError extends ExecutionError`
- 说明：Toolchain execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolchainError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ToolchainError(StatusCode status, Map<String, Object> params)` | - |
| `public ToolchainError(StatusCode status)` | - |

### `ValidationError`

- 类型：`class`
- 声明：`public class ValidationError extends BaseError`
- 说明：Constraint / validation / unsupported capability errors.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ValidationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public ValidationError(StatusCode status, Map<String, Object> params)` | - |
| `public ValidationError(StatusCode status)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected boolean defaultRecoverable()` | `boolean` | - |
| `protected boolean defaultFatal()` | `boolean` | - |

### `WorkflowError`

- 类型：`class`
- 声明：`public class WorkflowError extends ExecutionError`
- 说明：Workflow execution error.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | - |
| `public WorkflowError(StatusCode status, Map<String, Object> params)` | - |
| `public WorkflowError(StatusCode status)` | - |

## `com.openjiuwen.core.common.logging`

公开类型：`7`

### `LazyLogger`

- 类型：`class`
- 声明：`public class LazyLogger implements LoggerProtocol`
- 说明：Lazy initialization logger wrapper.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LazyLogger(java.util.function.Supplier<LoggerProtocol> getter)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void debug(String msg, Object... args)` | `void` | - |
| `public void info(String msg, Object... args)` | `void` | - |
| `public void warning(String msg, Object... args)` | `void` | - |
| `public void error(String msg, Object... args)` | `void` | - |
| `public void critical(String msg, Object... args)` | `void` | - |
| `public void exception(String msg, Throwable t, Object... args)` | `void` | - |
| `public void log(int level, String msg, Object... args)` | `void` | - |
| `public void setLevel(int level)` | `void` | - |
| `public void addHandler(Handler handler)` | `void` | - |
| `public void removeHandler(Handler handler)` | `void` | - |
| `public void addFilter(Filter filter)` | `void` | - |
| `public void removeFilter(Filter filter)` | `void` | - |
| `public Logger logger()` | `Logger` | - |
| `public java.util.Map<String, Object> getConfig()` | `java.util.Map<String, Object>` | - |
| `public void reconfigure(java.util.Map<String, Object> config)` | `void` | - |

### `LogManager`

- 类型：`class`
- 声明：`public final class LogManager`
- 说明：Log Manager \u2014 provides logger creation, registration, and retrieval.
- 嵌套公开类型：`LogManager.LoggerFactory`、`LogManager.LogConfigProvider`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void setDefaultLoggerFactory(LoggerFactory factory)` | `void` | Set the default logger factory (e.g., DefaultLogger::new). |
| `public static synchronized void initialize()` | `void` | Initialize the logging system. |
| `public static void registerLogger(String logType, LoggerProtocol logger)` | `void` | Register a custom logger for a given log type. |
| `public static LoggerProtocol getLogger(String logType)` | `LoggerProtocol` | Get a logger by type. |
| `public static Map<String, LoggerProtocol> getAllLoggers()` | `Map<String, LoggerProtocol>` | Get all registered loggers. |
| `public static synchronized void reset()` | `void` | Reset the log manager \u2014 primarily for testing. |

### `LogManager.LogConfigProvider`

- 类型：`class`
- 声明：`public static final class LogConfigProvider`
- 说明：Simple provider interface for log configuration.
- 宿主类型：`LogManager`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void setProvider(java.util.function.Supplier<Map<String, Map<String, Object>>> p)` | `void` | - |

### `LogManager.LoggerFactory`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface LoggerFactory`
- 说明：Functional interface for creating loggers from a type name and config.
- 宿主类型：`LogManager`
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `LoggerProtocol create(String logType, Map<String, Object> config)` | `LoggerProtocol` | - |

### `LoggerProtocol`

- 类型：`interface`
- 声明：`public interface LoggerProtocol`
- 说明：Logger protocol \u2014 every logger implementation must satisfy this contract.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void debug(String msg, Object... args)` | `void` | - |
| `void info(String msg, Object... args)` | `void` | - |
| `void warning(String msg, Object... args)` | `void` | - |
| `default void warn(String msg, Object... args)` | `void` | - |
| `void error(String msg, Object... args)` | `void` | - |
| `void critical(String msg, Object... args)` | `void` | - |
| `void exception(String msg, Throwable t, Object... args)` | `void` | Log exception with stack trace. |
| `void log(int level, String msg, Object... args)` | `void` | - |
| `void setLevel(int level)` | `void` | - |
| `default void addHandler(Handler handler)` | `void` | Add a log handler. |
| `default void removeHandler(Handler handler)` | `void` | Remove a log handler. |
| `default void addFilter(Filter filter)` | `void` | Add a log filter. |
| `default void removeFilter(Filter filter)` | `void` | Remove a log filter. |
| `default Logger logger()` | `Logger` | Return the inner logger object. |
| `Map<String, Object> getConfig()` | `Map<String, Object>` | Get logger configuration. |
| `void reconfigure(Map<String, Object> config)` | `void` | Reconfigure logger with new config. |

### `Loggers`

- 类型：`class`
- 声明：`public final class Loggers`
- 说明：Pre-defined loggers for each module \u2014 lazy-initialized singletons.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `COMMON` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("common"))` | - |
| `INTERFACE` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("interface"))` | - |
| `PERFORMANCE` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("performance"))` | - |
| `PROMPT_BUILDER` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("prompt_builder"))` | - |
| `AGENT` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("agent"))` | - |
| `MULTI_AGENT` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("multi_agent"))` | - |
| `WORKFLOW` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("workflow"))` | - |
| `SESSION` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("session"))` | - |
| `CONTROLLER` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("controller"))` | - |
| `RUNNER` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("runner"))` | - |
| `SYS_OPERATION` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("sys_operation"))` | - |
| `LLM` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("llm"))` | - |
| `TOOL` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("tool"))` | - |
| `PROMPT` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("prompt"))` | - |
| `STORE` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("store"))` | - |
| `MEMORY` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("memory"))` | - |
| `RETRIEVAL` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("retrieval"))` | - |
| `CONTEXT_ENGINE` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("context_engine"))` | - |
| `GRAPH` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("graph"))` | - |
| `OPERATOR` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("operator"))` | - |
| `MCP` | `LoggerProtocol` | `public static final` | `new LazyLogger(()->LogManager.getLogger("mcp"))` | - |

### `LoggingUtils`

- 类型：`class`
- 声明：`public final class LoggingUtils`
- 说明：Logging utility functions.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void setSessionId(String traceId)` | `void` | Set trace / session ID in current thread context. |
| `public static String getSessionId()` | `String` | Get trace / session ID from current thread context. |
| `public static void clearSessionId()` | `void` | Clear the current thread's trace ID (useful for thread-pool cleanup). |
| `public static int getLogMaxBytes(Object maxBytesConfig)` | `int` | Parse and validate max_bytes config value. |
| `public static String normalizeAndValidateLogPath(Object pathValue)` | `String` | Normalize log path (resolve to real path) and validate it is not a sensitive path. |

## `com.openjiuwen.core.common.logging.defaults`

公开类型：`4`

### `ConfigManager`

- 类型：`class`
- 声明：`public class ConfigManager`
- 说明：Configuration manager \u2014 loads YAML config and provides dot-notation access.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ConfigManager()` | - |
| `public ConfigManager(String configPath)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void reload(String configPath)` | `void` | - |
| `public Object get(String key, Object defaultValue)` | `Object` | Get a value by dot-separated key path. |
| `public Object get(String key)` | `Object` | - |
| `public Map<String, Object> getConfig()` | `Map<String, Object>` | - |

### `DefaultLogConstants`

- 类型：`class`
- 声明：`public final class DefaultLogConstants`
- 说明：Default log configuration constants.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_LEVEL` | `String` | `public static final` | `"INFO"` | - |
| `DEFAULT_LOG_PATH` | `String` | `public static final` | `"./logs/"` | - |
| `DEFAULT_LOG_FILE` | `String` | `public static final` | `"run/jiuwen.log"` | - |
| `DEFAULT_INTERFACE_LOG_FILE` | `String` | `public static final` | `"interface/jiuwen_interface.log"` | - |
| `DEFAULT_PROMPT_BUILDER_LOG_FILE` | `String` | `public static final` | `"interface/jiuwen_prompt_builder_interface.log"` | - |
| `DEFAULT_PERFORMANCE_LOG_FILE` | `String` | `public static final` | `"performance/jiuwen_performance.log"` | - |
| `DEFAULT_BACKUP_COUNT` | `int` | `public static final` | `20` | - |
| `DEFAULT_MAX_BYTES` | `int` | `public static final` | `20 * 1024 * 1024` | - |
| `DEFAULT_FORMAT` | `String` | `public static final` | `"%d{yyyy-MM-dd HH:mm:ss.SSS} \| %X{log_type} \| %file \| %line \| %method \| %X{trace_id} \| %-5level \| %msg%n"` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> defaultInnerLogConfig()` | `Map<String, Object>` | Build the default inner log config as a map. |
| `public static Map<String, Object> defaultLogConfig()` | `Map<String, Object>` | Build the default top-level config map. |

### `DefaultLogger`

- 类型：`class`
- 声明：`public class DefaultLogger implements LoggerProtocol`
- 说明：Default logger implementation backed by SLF4J + Logback.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DefaultLogger(String logType, Map<String, Object> config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void debug(String msg, Object... args)` | `void` | - |
| `public void info(String msg, Object... args)` | `void` | - |
| `public void warning(String msg, Object... args)` | `void` | - |
| `public void error(String msg, Object... args)` | `void` | - |
| `public void critical(String msg, Object... args)` | `void` | - |
| `public void exception(String msg, Throwable t, Object... args)` | `void` | - |
| `public void log(int level, String msg, Object... args)` | `void` | - |
| `public void setLevel(int level)` | `void` | - |
| `public void addHandler(Handler handler)` | `void` | - |
| `public void removeHandler(Handler handler)` | `void` | - |
| `public void addFilter(Filter filter)` | `void` | - |
| `public void removeFilter(Filter filter)` | `void` | - |
| `public java.util.logging.Logger logger()` | `java.util.logging.Logger` | - |
| `public Map<String, Object> getConfig()` | `Map<String, Object>` | - |
| `public void reconfigure(Map<String, Object> newConfig)` | `void` | - |
| `public void logEvent(String msg, LogEventType eventType, BaseLogEvent event)` | `void` | Log a structured event. |

### `LogConfig`

- 类型：`class`
- 声明：`public class LogConfig`
- 说明：Log configuration \u2014 resolves per-logger configs from a YAML file.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `logConfig` | `Map<String, Object>` | `private` | `-` | - |
| `logPath` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LogConfig()` | - |
| `public LogConfig(String configPath)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void reload(String configPath)` | `void` | - |
| `public Map<String, Object> getCommonConfig()` | `Map<String, Object>` | - |
| `public Map<String, Object> getInterfaceConfig()` | `Map<String, Object>` | - |
| `public Map<String, Object> getPromptBuilderConfig()` | `Map<String, Object>` | - |
| `public Map<String, Object> getPerformanceConfig()` | `Map<String, Object>` | - |
| `public Map<String, Object> getCustomConfig(String logType)` | `Map<String, Object>` | - |
| `public Map<String, Map<String, Object>> getAllConfigs()` | `Map<String, Map<String, Object>>` | Get all standard logger configurations. |

## `com.openjiuwen.core.common.logging.events`

公开类型：`23`

### `AgentEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class AgentEvent extends BaseLogEvent`
- 说明：Agent related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agentType` | `String` | `private` | `-` | - |
| `agentConfig` | `Map<String, Object>` | `private` | `-` | - |
| `inputData` | `Map<String, Object>` | `private` | `-` | - |
| `outputData` | `Map<String, Object>` | `private` | `-` | - |
| `iterationCount` | `Integer` | `private` | `-` | - |
| `maxIterations` | `Integer` | `private` | `-` | - |
| `executionTimeMs` | `Double` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `BaseLogEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder public class BaseLogEvent`
- 说明：Base log event class \u2014 base class for all structured event types.
- 注解：`@Data`、`@SuperBuilder`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `eventId` | `String` | `private` | `UUID.randomUUID().toString()` | - |
| `eventType` | `LogEventType` | `private` | `-` | - |
| `logLevel` | `LogLevel` | `private` | `LogLevel.INFO` | - |
| `timestamp` | `Instant` | `private` | `Instant.now()` | - |
| `moduleType` | `ModuleType` | `private` | `ModuleType.SYSTEM` | - |
| `moduleId` | `String` | `private` | `-` | - |
| `moduleName` | `String` | `private` | `-` | - |
| `sessionId` | `String` | `private` | `-` | - |
| `conversationId` | `String` | `private` | `-` | - |
| `traceId` | `String` | `private` | `-` | - |
| `correlationId` | `String` | `private` | `-` | - |
| `parentEventId` | `String` | `private` | `-` | - |
| `status` | `EventStatus` | `private` | `EventStatus.SUCCESS` | - |
| `errorCode` | `String` | `private` | `-` | - |
| `errorMessage` | `String` | `private` | `-` | - |
| `message` | `String` | `private` | `-` | - |
| `stacktrace` | `String` | `private` | `-` | - |
| `exceptionDetail` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseLogEvent()` | Default no-arg constructor for manual construction. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Convert to a flat map for serialization / structured logging output. |
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | Extension point for subclasses to add their own fields to the map. |
| `protected static void putIfNotNull(Map<String, Object> map, String key, Object value)` | `void` | - |

### `ContextEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class ContextEvent extends BaseLogEvent`
- 说明：Context operation related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messageType` | `String` | `private` | `-` | - |
| `messageContent` | `String` | `private` | `-` | - |
| `messageRole` | `String` | `private` | `-` | - |
| `contextSize` | `Integer` | `private` | `-` | - |
| `maxContextSize` | `Integer` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ContextEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `EventClassRegistry`

- 类型：`class`
- 声明：`public final class EventClassRegistry`
- 说明：Event class registry \u2014 maps LogEventType to concrete event class constructors.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void register(String eventTypeKey, Supplier<? extends BaseLogEvent> factory)` | `void` | Register a custom event class for a string event type. |
| `public static boolean unregister(String eventTypeKey)` | `boolean` | Unregister a custom event class. |
| `public static Supplier<? extends BaseLogEvent> getFactory(LogEventType eventType)` | `Supplier<? extends BaseLogEvent>` | Get event factory for a given event type (enum or string). |
| `public static Supplier<? extends BaseLogEvent> getFactory(String eventTypeKey)` | `Supplier<? extends BaseLogEvent>` | Get event factory by string key. |
| `public static BaseLogEvent createEvent(LogEventType eventType)` | `BaseLogEvent` | Create a log event of the appropriate type for the given event type. |

### `EventSanitizer`

- 类型：`class`
- 声明：`public final class EventSanitizer`
- 说明：Utility for sanitizing log events before output.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `REDACTED` | `String` | `public static final` | `"<REDACTED>"` | The placeholder string for redacted fields. |
| `DEFAULT_SENSITIVE_FIELDS` | `List<String>` | `public static final` | `List.of("messages", "response_content", "input_content", "query", "arguments", "result", "message_content", "tool_calls", "input_data", "output_data", "retrieved_memories")` | Default sensitive fields to sanitize. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event)` | `Map<String, Object>` | Sanitize event for logging output using default sensitive fields. |
| `public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event, List<String> sensitiveFields)` | `Map<String, Object>` | Sanitize event for logging output. |

### `EventStatus`

- 类型：`enum`
- 声明：`public enum EventStatus`
- 说明：Event status enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `SUCCESS` | `new EventStatus("success")` | - |
| `FAILURE` | `new EventStatus("failure")` | - |
| `PENDING` | `new EventStatus("pending")` | - |
| `TIMEOUT` | `new EventStatus("timeout")` | - |
| `CANCELLED` | `new EventStatus("cancelled")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `GraphEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class GraphEvent extends BaseLogEvent`
- 说明：Graph execution related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `graphId` | `String` | `private` | `-` | - |
| `nodeId` | `String` | `private` | `-` | - |
| `nodeName` | `String` | `private` | `-` | - |
| `inputs` | `Object` | `private` | `-` | - |
| `outputs` | `Object` | `private` | `-` | - |
| `chunk` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `LLMEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class LLMEvent extends BaseLogEvent`
- 说明：LLM call related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelName` | `String` | `private` | `-` | - |
| `modelProvider` | `String` | `private` | `-` | - |
| `query` | `String` | `private` | `-` | - |
| `messages` | `List<Map<String, Object>>` | `private` | `-` | - |
| `tools` | `List<Map<String, Object>>` | `private` | `-` | - |
| `temperature` | `Double` | `private` | `-` | - |
| `maxTokens` | `Integer` | `private` | `-` | - |
| `topP` | `Double` | `private` | `-` | - |
| `responseContent` | `String` | `private` | `-` | - |
| `toolCalls` | `List<Map<String, Object>>` | `private` | `-` | - |
| `usage` | `Map<String, Object>` | `private` | `-` | - |
| `latencyMs` | `Double` | `private` | `-` | - |
| `isStream` | `boolean` | `private` | `-` | - |
| `chunkIndex` | `Integer` | `private` | `-` | - |
| `extraParams` | `Map<String, Object>` | `private` | `-` | - |
| `timeout` | `Double` | `private` | `-` | - |
| `stop` | `String` | `private` | `-` | - |
| `maxRetries` | `Integer` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LLMEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `LogEventType`

- 类型：`enum`
- 声明：`public enum LogEventType`
- 说明：Log event type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `AGENT_START` | `new LogEventType("agent_start")` | - |
| `AGENT_END` | `new LogEventType("agent_end")` | - |
| `AGENT_INVOKE` | `new LogEventType("agent_invoke")` | - |
| `AGENT_RESPONSE` | `new LogEventType("agent_response")` | - |
| `AGENT_ERROR` | `new LogEventType("agent_error")` | - |
| `WORKFLOW_EXECUTE_START` | `new LogEventType("workflow_execute_start")` | - |
| `WORKFLOW_EXECUTE_END` | `new LogEventType("workflow_execute_end")` | - |
| `WORKFLOW_EXECUTE_ERROR` | `new LogEventType("workflow_execute_error")` | - |
| `WORKFLOW_OUTPUT_CHUNK` | `new LogEventType("workflow_output_chunk")` | - |
| `WORKFLOW_COMPONENT_START` | `new LogEventType("workflow_component_start")` | - |
| `WORKFLOW_COMPONENT_END` | `new LogEventType("workflow_component_end")` | - |
| `WORKFLOW_COMPONENT_ERROR` | `new LogEventType("workflow_component_error")` | - |
| `WORKFLOW_BRANCH` | `new LogEventType("workflow_branch")` | - |
| `LLM_CALL_START` | `new LogEventType("llm_call_start")` | - |
| `LLM_CALL_END` | `new LogEventType("llm_call_end")` | - |
| `LLM_CALL_ERROR` | `new LogEventType("llm_call_error")` | - |
| `LLM_STREAM_CHUNK` | `new LogEventType("llm_stream_chunk")` | - |
| `TOOL_CALL_START` | `new LogEventType("tool_call_start")` | - |
| `TOOL_CALL_END` | `new LogEventType("tool_call_end")` | - |
| `TOOL_CALL_ERROR` | `new LogEventType("tool_call_error")` | - |
| `STORE_ADD` | `new LogEventType("store_add")` | - |
| `STORE_DELETE` | `new LogEventType("store_delete")` | - |
| `STORE_UPDATE` | `new LogEventType("store_update")` | - |
| `STORE_RETRIEVE` | `new LogEventType("store_retrieve")` | - |
| `STORE_LOAD` | `new LogEventType("store_load")` | - |
| `MEMORY_STORE` | `new LogEventType("memory_store")` | - |
| `MEMORY_INIT` | `new LogEventType("memory_init")` | - |
| `MEMORY_RETRIEVE` | `new LogEventType("memory_retrieve")` | - |
| `MEMORY_DELETE` | `new LogEventType("memory_delete")` | - |
| `MEMORY_UPDATE` | `new LogEventType("memory_update")` | - |
| `MEMORY_PROCESS` | `new LogEventType("memory_process")` | - |
| `SESSION_CREATE` | `new LogEventType("session_create")` | - |
| `SESSION_UPDATE` | `new LogEventType("session_update")` | - |
| `SESSION_DELETE` | `new LogEventType("session_delete")` | - |
| `CONTEXT_ADD_MESSAGE` | `new LogEventType("context_add_message")` | - |
| `CONTEXT_CLEAR` | `new LogEventType("context_clear")` | - |
| `CONTEXT_RETRIEVE` | `new LogEventType("context_retrieve")` | - |
| `CONTEXT_SAVE` | `new LogEventType("context_save")` | - |
| `RETRIEVAL_START` | `new LogEventType("retrieval_start")` | - |
| `RETRIEVAL_END` | `new LogEventType("retrieval_end")` | - |
| `RETRIEVAL_ERROR` | `new LogEventType("retrieval_error")` | - |
| `PERFORMANCE_METRIC` | `new LogEventType("performance_metric")` | - |
| `USER_INPUT` | `new LogEventType("user_input")` | - |
| `USER_FEEDBACK` | `new LogEventType("user_feedback")` | - |
| `SYSTEM_START` | `new LogEventType("system_start")` | - |
| `SYSTEM_SHUTDOWN` | `new LogEventType("system_shutdown")` | - |
| `SYSTEM_ERROR` | `new LogEventType("system_error")` | - |
| `SYS_OP_START` | `new LogEventType("sys_operation_start")` | - |
| `SYS_OP_END` | `new LogEventType("sys_operation_end")` | - |
| `SYS_OP_ERROR` | `new LogEventType("sys_operation_error")` | - |
| `SYS_OP_STREAM` | `new LogEventType("sys_operation_stream")` | - |
| `CHECKPOINT_SAVE` | `new LogEventType("checkpoint_save")` | - |
| `CHECKPOINT_RESTORE` | `new LogEventType("checkpoint_restore")` | - |
| `CHECKPOINT_CLEAR` | `new LogEventType("checkpoint_clear")` | - |
| `CHECKPOINT_ERROR` | `new LogEventType("checkpoint_error")` | - |
| `CHECKPOINTER_STORE_ADD` | `new LogEventType("checkpointer_store_add")` | - |
| `CHECKPOINTER_STORE_REMOVE` | `new LogEventType("checkpointer_store_remove")` | - |
| `GRAPH_STREAM_CHUNK` | `new LogEventType("graph_stream_chunk")` | - |
| `GRAPH_SEND_STREAM_CHUNK` | `new LogEventType("graph_send_stream_chunk")` | - |
| `GRAPH_RECEIVE_STREAM_CHUNK` | `new LogEventType("graph_receive_stream_chunk")` | - |
| `SESSION_STREAM_CHUNK` | `new LogEventType("session_stream_chunk")` | - |
| `SESSION_STREAM_ERROR` | `new LogEventType("session_stream_error")` | - |
| `GRAPH_VERTEX_INIT` | `new LogEventType("graph_vertex_init")` | - |
| `GRAPH_VERTEX_CALL_START` | `new LogEventType("graph_vertex_call_start")` | - |
| `GRAPH_VERTEX_CALL_END` | `new LogEventType("graph_vertex_call_end")` | - |
| `GRAPH_VERTEX_CALL_ERROR` | `new LogEventType("graph_vertex_call_error")` | - |
| `GRAPH_VERTEX_STREAM_ACTOR_START` | `new LogEventType("graph_vertex_stream_actor_start")` | - |
| `GRAPH_VERTEX_STREAM_ACTOR_SHUTDOWN` | `new LogEventType("graph_vertex_stream_actor_shutdown")` | - |
| `GRAPH_VERTEX_STREAM_CALL_START` | `new LogEventType("graph_vertex_stream_call_start")` | - |
| `GRAPH_VERTEX_STREAM_CALL_END` | `new LogEventType("graph_vertex_stream_call_end")` | - |
| `GRAPH_VERTEX_STREAM_CALL_ERROR` | `new LogEventType("graph_vertex_stream_call_error")` | - |
| `GRAPH_VERTEX_ABILITY_START` | `new LogEventType("graph_vertex_ability_start")` | - |
| `GRAPH_VERTEX_ABILITY_RUNNING` | `new LogEventType("graph_vertex_ability_running")` | - |
| `GRAPH_VERTEX_ABILITY_END` | `new LogEventType("graph_vertex_ability_end")` | - |
| `GRAPH_VERTEX_ABILITY_ERROR` | `new LogEventType("graph_vertex_ability_error")` | - |
| `GRAPH_SUPER_STEP_START` | `new LogEventType("graph_super_step_start")` | - |
| `GRAPH_SUPER_STEP_END` | `new LogEventType("graph_super_step_end")` | - |
| `GRAPH_SUPER_STEP_ERROR` | `new LogEventType("graph_super_step_error")` | - |
| `GRAPH_START` | `new LogEventType("graph_start")` | - |
| `GRAPH_END` | `new LogEventType("graph_end")` | - |
| `GRAPH_ERROR` | `new LogEventType("graph_error")` | - |
| `GRAPH_STORE_SAVE` | `new LogEventType("graph_store_save")` | - |
| `GRAPH_STORE_DELETE` | `new LogEventType("graph_store_delete")` | - |
| `GRAPH_STORE_GET` | `new LogEventType("graph_store_get")` | - |
| `RUNNER_START` | `new LogEventType("runner_start")` | - |
| `RUNNER_STOP` | `new LogEventType("runner_stop")` | - |
| `RESOURCE_MGR_ADD_RESOURCE` | `new LogEventType("add_resource")` | - |
| `RESOURCE_MGR_REMOVE_RESOURCE` | `new LogEventType("remove_resource")` | - |
| `RESOURCE_MGR_GET_RESOURCE` | `new LogEventType("get_resource")` | - |
| `RESOURCE_MGR_ADD_RESOURCE_SERVER` | `new LogEventType("add_resource_server")` | - |
| `RESOURCE_MGR_REMOVE_RESOURCE_SERVER` | `new LogEventType("remove_resource_server")` | - |
| `RESOURCE_MGR_REMOVE_TAG` | `new LogEventType("remove_tag")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static LogEventType fromValue(String value)` | `LogEventType` | Lookup by string value, returns null if not found. |

### `LogLevel`

- 类型：`enum`
- 声明：`public enum LogLevel`
- 说明：Log level enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `DEBUG` | `new LogLevel("DEBUG")` | - |
| `INFO` | `new LogLevel("INFO")` | - |
| `WARNING` | `new LogLevel("WARNING")` | - |
| `ERROR` | `new LogLevel("ERROR")` | - |
| `CRITICAL` | `new LogLevel("CRITICAL")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `MemoryEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class MemoryEvent extends BaseLogEvent`
- 说明：Memory operation related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memoryType` | `String` | `private` | `-` | - |
| `operation` | `String` | `private` | `-` | - |
| `memoryId` | `List<String>` | `private` | `-` | - |
| `query` | `String` | `private` | `-` | - |
| `memoryCount` | `Integer` | `private` | `-` | - |
| `retrievedMemories` | `List<Map<String, Object>>` | `private` | `-` | - |
| `storageSizeBytes` | `Integer` | `private` | `-` | - |
| `userId` | `String` | `private` | `-` | - |
| `scopeId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MemoryEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `ModuleType`

- 类型：`enum`
- 声明：`public enum ModuleType`
- 说明：Module type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `AGENT` | `new ModuleType("agent")` | - |
| `WORKFLOW` | `new ModuleType("workflow")` | - |
| `WORKFLOW_COMPONENT` | `new ModuleType("workflow_component")` | - |
| `LLM` | `new ModuleType("llm")` | - |
| `TOOL` | `new ModuleType("tool")` | - |
| `STORE` | `new ModuleType("store")` | - |
| `MEMORY` | `new ModuleType("memory")` | - |
| `SESSION` | `new ModuleType("session")` | - |
| `CONTEXT` | `new ModuleType("context")` | - |
| `RETRIEVAL` | `new ModuleType("retrieval")` | - |
| `SYSTEM` | `new ModuleType("system")` | - |
| `USER` | `new ModuleType("user")` | - |
| `SYS_OPERATION` | `new ModuleType("sys_operation")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `PerformanceEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class PerformanceEvent extends BaseLogEvent`
- 说明：Performance metric related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `metricName` | `String` | `private` | `-` | - |
| `metricValue` | `Double` | `private` | `-` | - |
| `metricUnit` | `String` | `private` | `-` | - |
| `resourceType` | `String` | `private` | `-` | - |
| `operation` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PerformanceEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `RetrievalEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class RetrievalEvent extends BaseLogEvent`
- 说明：Retrieval related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `retrievalType` | `String` | `private` | `-` | - |
| `query` | `String` | `private` | `-` | - |
| `topK` | `Integer` | `private` | `-` | - |
| `retrievedDocs` | `List<Map<String, Object>>` | `private` | `-` | - |
| `retrievalScore` | `Double` | `private` | `-` | - |
| `latencyMs` | `Double` | `private` | `-` | - |
| `knowledgeBaseId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RetrievalEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `RunnerEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class RunnerEvent extends BaseLogEvent`
- 说明：Runner event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `runnerId` | `String` | `private` | `-` | - |
| `inputs` | `Object` | `private` | `-` | - |
| `outputs` | `Object` | `private` | `-` | - |
| `chunk` | `Object` | `private` | `-` | - |
| `envs` | `Object` | `private` | `-` | - |
| `resourceId` | `String` | `private` | `-` | - |
| `resourceType` | `String` | `private` | `-` | - |
| `tag` | `Object` | `private` | `-` | - |
| `card` | `BaseCard` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RunnerEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `SessionEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class SessionEvent extends BaseLogEvent`
- 说明：Session management related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sessionType` | `String` | `private` | `-` | - |
| `userId` | `String` | `private` | `-` | - |
| `agentId` | `String` | `private` | `-` | - |
| `workflowId` | `String` | `private` | `-` | - |
| `sessionConfig` | `Map<String, Object>` | `private` | `-` | - |
| `messageCount` | `Integer` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SessionEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `StoreEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class StoreEvent extends BaseLogEvent`
- 说明：Data store related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `tableName` | `String` | `private` | `-` | - |
| `dataNum` | `Integer` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StoreEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `StreamEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class StreamEvent extends BaseLogEvent`
- 说明：Stream related event \u2014 base class for all streaming events.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `streamType` | `String` | `private` | `-` | - |
| `chunkIndex` | `Integer` | `private` | `-` | - |
| `frameCount` | `Integer` | `private` | `-` | - |
| `streamId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `SysOperationEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class SysOperationEvent extends BaseLogEvent`
- 说明：SysOperation event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `operationName` | `String` | `private` | `-` | - |
| `operationMode` | `String` | `private` | `-` | - |
| `operationDesc` | `String` | `private` | `-` | - |
| `methodName` | `String` | `private` | `-` | - |
| `methodParams` | `Map<String, Object>` | `private` | `-` | - |
| `methodResult` | `Map<String, Object>` | `private` | `-` | - |
| `methodExecTimeMs` | `Double` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SysOperationEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `SystemEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class SystemEvent extends BaseLogEvent`
- 说明：System-level event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `systemVersion` | `String` | `private` | `-` | - |
| `systemConfig` | `Map<String, Object>` | `private` | `-` | - |
| `resourceUsage` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SystemEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `ToolEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class ToolEvent extends BaseLogEvent`
- 说明：Tool call related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `toolName` | `String` | `private` | `-` | - |
| `toolType` | `String` | `private` | `-` | - |
| `toolDescription` | `String` | `private` | `-` | - |
| `arguments` | `Map<String, Object>` | `private` | `-` | - |
| `result` | `Object` | `private` | `-` | - |
| `executionTimeMs` | `Double` | `private` | `-` | - |
| `toolCallId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `UserInteractionEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class UserInteractionEvent extends BaseLogEvent`
- 说明：User interaction related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `userId` | `String` | `private` | `-` | - |
| `inputContent` | `String` | `private` | `-` | - |
| `feedbackType` | `String` | `private` | `-` | - |
| `feedbackContent` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public UserInteractionEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

### `WorkflowEvent`

- 类型：`class`
- 声明：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = true) public class WorkflowEvent extends BaseLogEvent`
- 说明：Workflow related event.
- 注解：`@Data`、`@SuperBuilder`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `workflowId` | `String` | `private` | `-` | - |
| `workflowName` | `String` | `private` | `-` | - |
| `componentId` | `String` | `private` | `-` | - |
| `componentName` | `String` | `private` | `-` | - |
| `componentTypeStr` | `String` | `private` | `-` | - |
| `branchCondition` | `String` | `private` | `-` | - |
| `selectedBranch` | `String` | `private` | `-` | - |
| `inputs` | `Map<String, Object>` | `private` | `-` | - |
| `outputs` | `Object` | `private` | `-` | - |
| `chunk` | `Object` | `private` | `-` | - |
| `chunkIdx` | `Integer` | `private` | `-` | - |
| `outputData` | `Map<String, Object>` | `private` | `-` | - |
| `executionTimeMs` | `Double` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void addFieldsToMap(Map<String, Object> map)` | `void` | - |

## `com.openjiuwen.core.common.schema`

公开类型：`4`

### `BaseCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor public class BaseCard`
- 说明：Base digital card \u2014 the root class for all card-like entities.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `UUID.randomUUID().toString().replace("-", "")` | Unique identifier (UUID hex by default). |
| `name` | `String` | `private` | `""` | Name \u2014 also serves as the unique identifier in a namespace. |
| `description` | `String` | `private` | `""` | Description of functionality, applicable scenarios, etc. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object toolInfo()` | `Object` | Override in subclasses to provide tool-specific information. |
| `public BaseCard copy()` | `BaseCard` | Create a shallow copy of this card. |
| `public String toString()` | `String` | - |

### `Param`

- 类型：`class`
- 声明：`public class Param`
- 说明：Parameter definition model with nested structure support.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `description` | `String` | `private final` | `-` | - |
| `type` | `ParamType` | `private final` | `-` | - |
| `required` | `boolean` | `private final` | `-` | - |
| `defaultValue` | `Object` | `private final` | `-` | - |
| `items` | `Param` | `private final` | `-` | - |
| `properties` | `List<Param>` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public String getDescription()` | `String` | - |
| `public ParamType getType()` | `ParamType` | - |
| `public boolean isRequired()` | `boolean` | - |
| `public Object getDefaultValue()` | `Object` | - |
| `public Param getItems()` | `Param` | - |
| `public List<Param> getProperties()` | `List<Param>` | - |
| `public static Param string(String name, String description, boolean required)` | `Param` | Create a string type parameter. |
| `public static Param string(String name, String description, boolean required, String defaultValue)` | `Param` | - |
| `public static Param bool(String name, String description, boolean required)` | `Param` | Create a boolean type parameter. |
| `public static Param bool(String name, String description, boolean required, Boolean defaultValue)` | `Param` | - |
| `public static Param integer(String name, String description, boolean required)` | `Param` | Create an integer type parameter. |
| `public static Param integer(String name, String description, boolean required, Integer defaultValue)` | `Param` | - |
| `public static Param number(String name, String description, boolean required)` | `Param` | Create a number (float/double) type parameter. |
| `public static Param number(String name, String description, boolean required, Double defaultValue)` | `Param` | - |
| `public static Param array(String name, String description, boolean required, Param items)` | `Param` | Create an array type parameter. |
| `public static Param array(String name, String description, boolean required, Param items, Object defaultValue)` | `Param` | - |
| `public static Param object(String name, String description, boolean required, List<Param> properties)` | `Param` | Create an object type parameter. |
| `public static Param object(String name, String description, boolean required, List<Param> properties, Object defaultValue)` | `Param` | - |
| `public String toString()` | `String` | - |

### `ParamType`

- 类型：`enum`
- 声明：`public enum ParamType`
- 说明：Parameter type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `STRING` | `new ParamType("string")` | - |
| `BOOLEAN` | `new ParamType("boolean")` | - |
| `INTEGER` | `new ParamType("integer")` | - |
| `NUMBER` | `new ParamType("number")` | - |
| `ARRAY` | `new ParamType("array")` | - |
| `OBJECT` | `new ParamType("object")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static ParamType fromValue(String value)` | `ParamType` | - |

### `Part`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class Part`
- 说明：Part data model - represents a content part within an artifact.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `-` | - |
| `content` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

## `com.openjiuwen.core.common.security`

公开类型：`6`

### `ExceptionUtils`

- 类型：`class`
- 声明：`public final class ExceptionUtils`
- 说明：Exception formatting utilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String formatValidationError(Throwable t)` | `String` | Format a validation exception's errors into a human-readable multi-line string. |
| `public static Throwable getRootCause(Throwable t)` | `Throwable` | Get the root cause of an exception chain. |

### `JsonUtils`

- 类型：`class`
- 声明：`public final class JsonUtils`
- 说明：Safe JSON serialization/deserialization utilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static <T>T safeJsonLoads(String json, Class<T> type, T defaultValue)` | `T` | Safely parse a JSON string. |
| `public static <T>T safeJsonLoads(String json, Class<T> type)` | `T` | Parse JSON string \u2014 throws on error. |
| `public static String safeJsonDumps(Object obj, String defaultValue)` | `String` | Safely serialize an object to JSON. |
| `public static String safeJsonDumps(Object obj)` | `String` | Serialize to JSON \u2014 throws on error. |
| `public static ObjectMapper getMapper()` | `ObjectMapper` | Get the shared ObjectMapper instance for advanced usage. |

### `PathChecker`

- 类型：`class`
- 声明：`public final class PathChecker`
- 说明：Path checker \u2014 singleton that determines whether a file path is sensitive.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static PathChecker getInstance()` | `PathChecker` | Get or create the singleton instance. |
| `public boolean checkSensitive(String path)` | `boolean` | Check if a path is sensitive. |
| `public static boolean isSensitivePath(String path)` | `boolean` | Convenience static method. |

### `SslUtils`

- 类型：`class`
- 声明：`public final class SslUtils`
- 说明：SSL utilities \u2014 creates strict SSL contexts for secure HTTPS communication.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static SSLContext createStrictSslContext(String sslCertPath)` | `SSLContext` | Create a strict SSLContext optionally loading a CA certificate. |
| `public static SSLContext createInsecureSslContext()` | `SSLContext` | Create an insecure SSL context that trusts every certificate. |
| `public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv, java.util.List<String> triggerValues, boolean urlIsHttps)` | `Object[]` | Get SSL config based on environment variables. |

### `UrlUtils`

- 类型：`class`
- 声明：`public final class UrlUtils`
- 说明：URL validation and proxy utilities \u2014 protects against SSRF attacks.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void checkUrlIsValid(String url)` | `void` | Validate that a URL is well-formed, uses http(s), and does not resolve to an internal IP. |
| `public static String getGlobalProxyUrl(String url)` | `String` | Get the global proxy URL from environment variables, respecting NO_PROXY. |
| `public static Map<String, String> getGlobalProxies(String url)` | `Map<String, String>` | Get global proxies as a map (http \u2192 proxy, https \u2192 proxy). |
| `public static boolean shouldBypassProxy(String url)` | `boolean` | Check if the URL should bypass proxying based on NO_PROXY. |

### `UserConfig`

- 类型：`class`
- 声明：`public final class UserConfig`
- 说明：User configuration \u2014 singleton that reads security settings from a properties/ini file.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_SENSITIVE_PATHS` | `List<String>` | `public static final` | `List.of("/etc/passwd", "/etc/shadow", "/etc/hosts", "/etc/hostname", "/etc/ssh/", "C:\\Windows\\System32\\", "C:\\Windows\\SysWOW64\\", "C:\\Windows\\System\\")` | - |
| `sensitive` | `boolean` | `private final` | `-` | - |
| `sensitivePaths` | `List<String>` | `private volatile` | `-` | - |
| `properties` | `Properties` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void setConfigPath(Path path)` | `void` | Set config path \u2014 must be called before first access. |
| `public static UserConfig getConfig()` | `UserConfig` | Get the singleton config instance. |
| `public static boolean isSensitive()` | `boolean` | Whether sensitivity checking is enabled. |
| `public static List<String> getSensitivePaths()` | `List<String>` | Get the list of sensitive paths. |
| `public static synchronized void reset()` | `void` | Reset singleton \u2014 primarily for testing. |

## `com.openjiuwen.core.common.utils`

公开类型：`6`

### `DictUtils`

- 类型：`class`
- 声明：`public final class DictUtils`
- 说明：Utility functions for nested Map (dictionary) manipulation.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Object createNestedMap(String path, Object value, String separator)` | `Object` | Create a nested Map from a dotted path string. |
| `public static Object createNestedMap(String path, Object value)` | `Object` | Overload with default "." separator. |
| `public static Map<String, Object> flattenMap(Map<String, Object> data)` | `Map<String, Object>` | Flatten a nested map into a single-level map with dotted-path keys. |
| `public static List<Map.Entry<List<String>, Object>> extractLeafNodes(Object data, List<String> currentPath)` | `List<Map.Entry<List<String>, Object>>` | Extract all leaf nodes from a nested dict/list structure. |
| `public static String formatPath(List<String> path)` | `String` | Format a path list into a dotted string. |
| `public static Map<String, Object> rebuildMapFromPaths(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | `Map<String, Object>` | Rebuild a nested Map from (path, value) pairs (dict-keys only, no list indices). |
| `public static Map<String, Object> rebuildDict(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | `Map<String, Object>` | Rebuild a nested structure (Maps/Lists) from path-value pairs. |

### `HashUtil`

- 类型：`class`
- 声明：`public final class HashUtil`
- 说明：Hash utility \u2014 generates deterministic SHA-256 keys from API credentials.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String generateKey(String apiKey, String apiBase, String modelProvider)` | `String` | Generate a deterministic SHA-256 hex key from API key, base URL, and model provider. |
| `public static String generateKey(String apiKey, String apiBase)` | `String` | Overload with default modelProvider = "openai". |

### `IpUtils`

- 类型：`class`
- 声明：`public final class IpUtils`
- 说明：IP utility \u2014 discovers the local (non-loopback) IPv4 address.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String getLocalIp()` | `String` | Get the local available IPv4 address (excluding 127.0.0.1). |

### `MessageUtils`

- 类型：`class`
- 声明：`public final class MessageUtils`
- 说明：Message utilities for adding and retrieving messages in the context engine.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static boolean shouldAddUserMessage(String query, ContextEngine contextEngine, Session session)` | `boolean` | Check if a user message should be added (deduplication). |
| `public static void addUserMessage(Object query, ContextEngine contextEngine, Session session)` | `void` | Add a user message to the chat history. |
| `public static void addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine, Session session)` | `void` | Add an assistant message to the chat history. |
| `public static void addToolMessage(ToolMessage toolMessage, ContextEngine contextEngine, Session session)` | `void` | Add a tool message to the chat history. |
| `public static void addWorkflowMessage(BaseMessage message, String workflowId, ContextEngine contextEngine, Session session)` | `void` | Add a message to a specific workflow's chat history. |
| `public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int maxRounds)` | `List<BaseMessage>` | Get chat history, limited by max rounds. |

### `SchemaUtils`

- 类型：`class`
- 声明：`public final class SchemaUtils`
- 说明：Schema utility class for handling JSON Schema validation, data formatting, and default value population.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema)` | `Map<String, Object>` | Format data according to the provided JSON Schema, filling in default values for missing properties. |
| `public static void validateWithSchema(Map<String, Object> data, Map<String, Object> schema)` | `void` | Validate data against a JSON Schema dictionary. |
| `public static Map<String, Object> getSchemaDict(Class<?> clazz)` | `Map<String, Object>` | Get a schema dictionary representation from a Java class using Jackson. |

### `SingletonSupport`

- 类型：`class`
- 声明：`public abstract class SingletonSupport<T>`
- 说明：Generic thread-safe singleton support using double-checked locking.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static <T>T getInstance(Class<T> clazz, java.util.function.Supplier<T> factory)` | `T` | Get or create the singleton instance. |
| `public static void reset(Class<?> clazz)` | `void` | Reset a specific singleton \u2014 primarily for testing. |

