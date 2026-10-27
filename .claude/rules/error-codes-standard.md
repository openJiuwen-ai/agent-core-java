---
description: StatusCode / BaseError / ErrorHelper usage conventions for agent-core-java. Enforces the unified error code system.
language: english
paths:
  - "src/main/java/com/openjiuwen/**/*.java"
---

# Error Code Rules

All exceptions raised inside the framework **must** go through the unified
`com.openjiuwen.core.common.exception` system. This file lists enforceable
hard rules. For design details, see the package-level Javadoc of
`com.openjiuwen.core.common.exception`.

## The Only Legal Way to Raise

Every exception must carry a `StatusCode`. Business code **must not** raise
bare `RuntimeException / IllegalStateException / IllegalArgumentException /
NullPointerException`.

```java
// Preferred: ErrorHelper.raiseError / buildError
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

ErrorHelper.raiseError(StatusCode.WORKFLOW_EXECUTION_ERROR, "reason=" + e.getMessage());
ErrorHelper.raiseError(StatusCode.TOOL_EXECUTION_ERROR, msg, details, e, params);
BaseError err = ErrorHelper.buildError(StatusCode.TOOL_EXECUTION_ERROR, "key", "value");

// Direct throw is allowed only when the concrete class needs extra constructor args
// (currently only ToolError.card / RunnerTermination.reason)
throw new ToolError(StatusCode.TOOL_EXECUTION_ERROR, toolCard, "reason=" + e.getMessage());

// Forbidden: bare RuntimeException, or BaseError without a StatusCode
throw new RuntimeException("workflow broken");
throw new IllegalStateException("bad state");
```

**Exceptions from external dependencies** must be caught at the module
boundary and re-raised as a `BaseError` subclass with the original exception
attached via `cause`. Do not let third-party exception types propagate up
the call stack.

```java
// OK: wrap at boundary
catch (IOException e) {
    throw ErrorHelper.raiseError(StatusCode.SYS_OPERATION_ERROR, "reason=" + e.getMessage(), null, e, null);
}

// Bad: let IOException leak upward
```

## StatusCode vs Exception Class Are Orthogonal

- **StatusCode** identifies the error (which module, which failure).
- **Exception class** encodes control-flow semantics (retry / abort / terminate).

| Exception Class | fatal | recoverable | Semantics |
|---|---|---|---|
| `FrameworkError` | true | false | Infrastructure broken; cannot continue |
| `ValidationError` | false | false | Invalid input/config; no retry will help |
| `ExecutionError` | false | true | Transient failure; retry may succeed |
| `Termination` | false | false | Non-error control-flow stop |

`StatusMapping` binds StatusCode to exception class.
`ErrorHelper.raiseError` / `buildError` resolve the class automatically.
**Do not guess the mapping** — get the naming right and the classification
follows.

## Adding a New StatusCode

New entries go into `StatusCode.java` and must satisfy all of the following:

1. **Code range** must fall in the numeric segment of the corresponding scope
   (see the range table below). Cross-segment values break the range-based
   fallback classification.

   | Range | Scope |
   |---|---|
   | 100000-100999 | Workflow |
   | 101000-119999 | Component |
   | 120000-129999 | Agent |
   | 130000-139999 | Runner |
   | 140000-149999 | Graph |
   | 150000-154999 | Context |
   | 155000-157999 | Retrieval |
   | 158000-159999 | Memory |
   | 160000-179999 | Toolchain |
   | 180000-180999 | Prompt |
   | 181000-181999 | Model |
   | 182000-182999 | Tool |
   | 188000-188999 | Common |
   | 190000-198999 | Session |
   | 199000-199999 | SysOperation |

2. **Name** follows `{SCOPE}_{SUBJECT}_{FAILURE_TYPE}`:
   - `SCOPE` must be in `StatusCodeTemplate.ALLOWED_SCOPES`
   - `FAILURE_TYPE` must be in `StatusCodeTemplate.ALLOWED_FAILURE_TYPES`
     (`INVALID / NOT_FOUND / PARAM_ERROR / CONFIG_ERROR / INIT_FAILED /
     CALL_FAILED / EXECUTION_ERROR / RUNTIME_ERROR / TIMEOUT / INTERRUPTED`, etc.)
3. **Message template** uses `{name}` placeholders — never `%s` or `+`
   concatenation. TIMEOUT templates must include `{timeout}`; generic errors
   should carry `{reason}` or `{error_msg}`.
4. **Code values must be unique.** Java `enum` does not silently alias
   duplicates, but grep the numeric value before adding to avoid confusion.
5. **Preserve the section comments.** The block-comment segmentation in
   `StatusCode.java` is the file's only human index. Do not scramble it.

No manual mapping registration is needed —
`StatusMapping.resolveExceptionFactory()` resolves based on name keywords
and code range at class-load time. If the default classification is wrong,
prefer **fixing the name** so it matches a keyword rule. Only fall back to
adding an entry in `StatusMapping.MANUAL_OVERRIDES` for genuine special
cases.

## Standalone Exceptions

A few exceptions extend `RuntimeException` directly instead of `BaseError`.
These are legacy or domain-specific exceptions that predate the unified
system. **Do not add new standalone exceptions.** Instead, add a new
`StatusCode` and use `ErrorHelper`.

Existing standalone exceptions (do not replicate this pattern):
- `GitHubError` — in `core.singleagent.skills`
- `ToolInterruptException` — in `core.singleagent.interrupt`
- `SandboxNotFoundException` / `SandboxOperationException` / `SandboxRecreateExhaustedException` — in `extensions.sys_operation.sandbox.providers.jiuwenbox`
- `HumanAgentNotEnabledError` / `UnknownHumanAgentError` — in `agentteams.interaction`

## Don'ts

- **Don't branch on `e.getStatus().getCode()`** in business code. For
  control-flow decisions use `instanceof ExecutionError` or read
  `e.isRecoverable()` / `e.isFatal()`.
- **Don't stuff runtime data into `StatusCode`.** The enum is an immutable
  constant. Dynamic fields go through the `details` map or `params` varargs.
- **Don't raise new exceptions on the error path.** `BaseError.renderMessage()`
  is lazy-safe by design — missing keys render as `<missing:key>` rather than
  throwing. Any extension to rendering must preserve this invariant.
- **Don't swallow exceptions with `catch (BaseError e) { }`.** Either let it
  propagate or convert it to an explicit StatusCode and re-raise.
- **Don't use `ErrorHelper.systemError()` / `validateError()` / `terminate()`
  when `raiseError()` would do.** The shorthand methods exist for
  disambiguation when the keyword-based mapping resolves incorrectly; they
  are not the default path.