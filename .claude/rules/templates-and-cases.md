---
description: Directs Claude to use templates, cases, and test-scenarios when creating code, fixing bugs, or writing tests.
language: english
paths:
  - "src/main/java/**/*.java"
  - "src/test/java/**/*.java"
alwaysApply: false
---

# Templates, Cases & Test Scenarios

## When creating new code

Start from the matching template in `.claude/templates/`:

| Target | Template |
|---|---|
| Card class (extends `BaseCard`) | `.claude/templates/card.java.tmpl` |
| Service / business class | `.claude/templates/service.java.tmpl` |
| Harness tool (returns `ToolOutput`) | `.claude/templates/tool.java.tmpl` |
| Test class | `.claude/templates/test.java.tmpl` |

Adding a new `StatusCode` entry → follow
`.claude/templates/exception.md.tmpl`.

`{{placeholders}}` in templates must be replaced with actual values.
Do not copy blindly — read nearby code in the target package to align
with local conventions.

## When fixing bugs

Search `.claude/cases/` for related cases first. Match by `module`
field or `tags`. If the fix reveals a pattern worth recording, add a
new case file.

## When writing tests

Check `.claude/test-scenarios/` for applicable scenarios that must be
covered. Match by `module` or `type`. If a scenario has
`Coverage: [ ]` (uncovered), add the corresponding test and flip it to
`[x]`.

When adding a new feature, create a `test-scenarios` entry for each P0
path (null input, boundary, concurrency, exception propagation) even if
the test is written immediately — this builds a regression net.

## Case vs Test-Scenario

| When to use Case | When to use Test-Scenario |
|---|---|
| An incident or tricky bug that already happened | An error path or boundary condition to cover |
| Has root-cause analysis and a fix | Only needs input and expected behavior |
| Post-hoc archive, looking back | Upfront design, preventing future |
| Template has `severity` / `date` | Template has `priority` / `type` |

Rule of thumb: **Something broke → log a Case; something might break → log a Test-Scenario.**
