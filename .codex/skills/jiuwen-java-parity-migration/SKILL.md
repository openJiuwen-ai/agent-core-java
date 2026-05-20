---
name: jiuwen-java-parity-migration
description: Use for migration work in this repository that translates Python agent-core behavior into Java without compatibility layers, fallback inventions, or test seams leaking into production APIs.
---

# Jiuwen Java Parity Migration

Use this skill before changing migration code in `/home/gallon/src/jiuwen-java/agent-core-java`.

## Hard Workflow

1. Read the module migration report and `documents/zh/3.迁移报告/源码对齐审计总表.md`.
2. Read the exact Python production source for the slice.
3. Read the exact Python tests for the slice.
4. Read the Java production source and tests.
5. Record a short contrast before editing:
   - Python production paths
   - Python test paths
   - Java production paths
   - Java test paths
   - Python production behavior being migrated
   - Python test seam or mock strategy
   - Java implementation boundary
6. Only then edit Java code.

## Anti-Design Rules

- Production API, constructor parameters, runtime fields, adapters, fallbacks, hooks, and helper objects must come from Python production behavior or existing Java runtime necessity.
- Do not add a production seam just to make Java tests convenient.
- If Python tests patch a factory/function, Java tests should use an equivalent test-only seam such as static mocking, a package-private helper, or a factory wrapper that does not change the production contract.
- If Java needs a different test seam because of language/runtime constraints, document it as a test-only carrying difference before implementing it.
- Do not infer missing behavior from Java style, prior slices, or convenience. Re-check Python source and tests for every slice.

## Pre-Edit Questions

Before every code change, answer these from source evidence:

1. Does Python production code have this behavior or extension point?
2. Do Python tests use this mocking or seam strategy?
3. If Java differs, is the difference only a test carrier and invisible to production behavior?

If the answer is "no" or unclear, stop and inspect more source instead of designing a new solution.

## Closeout

- Run focused tests covering the changed slice.
- Run `rtk git diff --check`.
- Update the module migration report and `documents/zh/3.迁移报告/源码对齐审计总表.md`.
- Do not mark a module aligned unless source paths, test paths, and remaining differences are explicitly audited.
