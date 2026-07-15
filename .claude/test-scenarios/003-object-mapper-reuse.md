---
id: 003
title: ObjectMapper must be class-level static final, never created inside methods
module: com.openjiuwen.core.common
priority: P0
type: resource
tags: [object-mapper, gc, performance, json]
---

## Description

ObjectMapper is expensive to create (builds serializer/deserializer
caches) but thread-safe and reusable. Any `new ObjectMapper()` inside
a method body causes GC pressure under frequent invocation. It must be
declared as a `private static final` class-level field, or obtained
via `JsonUtils.getMapper()`.

## Input Conditions

- Check all occurrences of `new ObjectMapper()`
- Distinguish class-level `static final` declarations (compliant) vs
  in-method creation (non-compliant)

## Expected Behavior

- All ObjectMapper instances are `private static final` fields
- No `new ObjectMapper()` inside method bodies
- High-frequency methods like `BaseError.toJson()` are especially checked

## Test Location

- `src/test/java/com/openjiuwen/core/common/security/JsonUtilsTest.java`
  - `testGetMapperReturnsSameInstance` — multiple calls return the same instance
- `src/test/java/com/openjiuwen/core/common/exception/BaseErrorTest.java`
  - `testToJsonDoesNotCreateNewObjectMapper` — multiple toJson calls do not create new instances

## Coverage

- [ ] not covered
