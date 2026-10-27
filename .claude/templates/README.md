# Code Templates

Scaffolding for new code in `agent-core-java`. Each template reflects
actual patterns observed in the codebase — Lombok usage on Cards, plain
POJO for services/tools, JUnit 5 + AssertJ + programmatic Mockito for
tests, `ErrorHelper` + `StatusCode` for errors.

## Usage

When creating a new class, start from the matching template and adapt.
Do not copy blindly — read nearby code in the target package first.

## Files

| Template | When to use |
|---|---|
| `card.java.tmpl` | New Card class extending `BaseCard` |
| `service.java.tmpl` | Service/business class with logger, config, error handling |
| `tool.java.tmpl` | Harness tool returning `ToolOutput` |
| `test.java.tmpl` | JUnit 5 unit test with AssertJ + Mockito |
| `exception.md.tmpl` | Adding a new `StatusCode` entry |

## Conventions (apply to all templates)

- Copyright header: `/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */`
- Javadoc: method name as summary, `@param name name`, `@return the result`
- Logger: `Loggers.XXX.error(...)` — never `System.out`, never direct `Logger`
- ObjectMapper: `private static final ObjectMapper MAPPER = new ObjectMapper();`
- Concurrency: `ConcurrentHashMap` for shared mutable maps
- Errors: `throw ErrorHelper.buildError(StatusCode.X, "key", "value")`
- Lombok: only on Card subclasses; services/tools are vanilla Java
