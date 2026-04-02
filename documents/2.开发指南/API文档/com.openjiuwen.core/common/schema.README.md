# schema

`com.openjiuwen.core.common.schema` defines the lightweight DTOs that describe cards, tool parameters, and structured content fragments across the framework.

## Core Types

| Type | Description |
| --- | --- |
| [`BaseCard`](./schema/BaseCard.md) | Root card model with identity, description, shallow-copy, and tool-info extension hooks. |
| [`Param`](./schema/Param.md) | Immutable parameter definition with factory methods for scalar, array, and object shapes. |
| [`ParamType`](./schema/ParamType.md) | Enum that normalizes the supported parameter kinds. |
| [`Part`](./schema/Part.md) | Lombok-backed content-part DTO with `type`, `content`, and `metadata`. |

## Notes

- `BaseCard` and `Part` rely on Lombok to generate accessors, builders, and constructor boilerplate.
- `Param` validates array/object shape rules at construction time, so invalid combinations fail early.
