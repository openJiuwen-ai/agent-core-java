# com.openjiuwen.core

`com.openjiuwen.core` is the namespace-level landing page for the openJiuwen Java core packages. Use it to enter each documented module tree before drilling into child packages or concrete type pages.

## Modules

| Module | Description |
| --- | --- |
| [`application`](./application.README.md) | Agent-facing application APIs, workflow entry points, and configuration DTOs. |
| [`common`](./common.README.md) | Shared exceptions, logging utilities, constants, schema helpers, and low-level support classes. |
| [`context`](./context.README.md) | Context-window management, message buffering, token counting, and processor integrations. |
| [`controller`](./controller.README.md) | Controller runtime contracts, configuration, and legacy controller support types. |
| [`foundation`](./foundation.README.md) | Foundation-layer model access, provider clients, output parsers, and core LLM schema types. |
| [`graph`](./graph.README.md) | Graph execution, Pregel helpers, stream actors, visualization, and storage abstractions. |
| [`multiagent`](./multiagent.README.md) | Multi-agent runtime coordination, group schema, and compatibility layers. |
| [`operator`](./operator.README.md) | Operator contracts and the LLM, memory, and tool-call operator implementations. |
| [`runner`](./runner.README.md) | Runtime orchestration, callbacks, MQ integration, and distributed-runner support. |
| [`security`](./security.README.md) | Guardrail contracts, risk assessment results, and user-input safety helpers. |
| [`session`](./session.README.md) | Session state, persistence, tracers, checkpointing, and stream-state models. |
| [`singleagent`](./singleagent.README.md) | Single-agent runtime, legacy rails, schemas, and skills-related helpers. |
| [`sysop`](./sysop.README.md) | System-operation facades, local/sandbox implementations, registry metadata, and result DTOs. |

## Reading Flow

- Start from the module README that matches the package you need.
- Use package README pages to move into child packages or jump to the main public types.
- `SUMMARY.md` mirrors the same tree so the package README pages and the global navigation stay aligned.
