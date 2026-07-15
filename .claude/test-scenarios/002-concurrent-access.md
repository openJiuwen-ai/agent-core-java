---
id: 002
title: Shared Map concurrent writes must use ConcurrentHashMap
module: com.openjiuwen.core.singleagent
priority: P0
type: concurrency
tags: [concurrency, hashmap, dead-loop, thread-safety]
---

## Description

Classes holding `Map<String, ToolCard>` / `Map<String, AgentCard>`
fields (e.g., `AbilityManager`) are read and written by multiple
threads simultaneously (e.g., multiple Teammates registering tools in
a Leader-Teammate team scenario). Using `HashMap` under concurrent
put in Java 8+ can cause internal linked-list cycles, triggering
infinite loops or data loss. `ConcurrentHashMap` is required (per
`X.CON.05`).

## Input Conditions

- 10 threads calling `registerTool()` / `getTool()` concurrently
- Each thread registers 100 distinct ToolCards
- Runs for 5 seconds
- Reader threads continuously iterate `getToolNames()`

## Expected Behavior

- No infinite loops (test completes within 10 seconds)
- No `ConcurrentModificationException`
- All registered tools are readable (count == 10 * 100)
- No data loss

## Test Location

- `src/test/java/com/openjiuwen/core/singleagent/AbilityManagerTest.java`
  - `testConcurrentRegisterAndGetNoException` — concurrent register + read
  - `testConcurrentRegisterNoDataLoss` — registration count integrity

- `src/test/java/com/openjiuwen/agentteams/agent/TeamAgentTest.java`
  - `testMultipleTeammatesRegisterToolsConcurrently` — multi-Teammate concurrent registration

## Coverage

- [ ] not covered
