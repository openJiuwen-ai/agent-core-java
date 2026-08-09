---
name: refactor-guide
description: Project refactoring standards and process governance. Automatically applied when users want to refactor code, split classes, extract methods, add tests, change signatures, or change architecture, ensuring refactoring is safe and verifiable. Related keywords: refactoring, refactor, extract method, split class, add tests, characterization test, Strangler Fig, L1-L4 refactoring granularity, safe refactoring. Not applicable to: coding style issues (use coding-standard), JVM troubleshooting (use jvm-troubleshoot), new feature development (not refactoring).
---

# Project Refactoring Standards and Process Governance

This skill addresses the core pain points of refactoring: **refactoring without tests, no verification after changes, interruptions mid-refactoring, introducing new bugs**. The principle is "add tests first, then verify; take small steps."

## Pre-Refactoring Checklist (Stop If Not Satisfied)

All of the following must be met before refactoring. No exceptions:

- [ ] **Sufficient test coverage**: Core logic coverage > 70%. No tests -> add "characterization tests" first (record current behavior, even if it has bugs), then refactor
- [ ] **Tests are currently green**: `mvn test` passes, establishing a "it was working before refactoring" baseline
- [ ] **Clear refactoring goal**: Can state in one sentence "what problem this refactoring solves", not "just tidying up while I'm at it"
- [ ] **Rollback plan**: Work on a separate git branch, or have a backup
- [ ] **Refactoring scope is defined**: List the files to be changed; do not change code outside the scope just because it "looks bad"

**How to write characterization tests** (emergency when there are no tests):
```java
// Record current behavior, do not judge right or wrong
@Test
void characterize_currentBehavior_ofCalc() {
    assertEquals(42, calc.compute(1, 2, 3));  // Even if 42 is a bug, record it first
    assertEquals(0, calc.compute(0, 0, 0));
    assertNull(calc.compute(null, null, null));  // Record current NPE too
}
```

## Refactoring Granularity Levels

4 levels by risk and impact scope, each with different process requirements:

| Level | Scope | Example | Process Requirements |
|---|---|---|---|
| **L1 Surface Refactoring** | Single file, no signature change | Rename variable, extract constant, format adjustment | Run unit tests after changes |
| **L2 Structural Refactoring** | Single file, change internal structure | Extract method, inline method, move function | Run unit tests + integration tests after changes |
| **L3 Interface Refactoring** | Multiple files, change signature | Change method signature, split class, merge classes | Change tests first, then implementation, run full test suite |
| **L4 Architecture Refactoring** | Cross-module, change dependencies | Switch framework, split microservices, change data flow | Phased approach, each phase independently testable and rollbackable |

**How to determine level**: If changes affect callers -> at least L3; if cross-module -> L4.

## Standard Refactoring Process (5 Steps, Small Steps)

1. **Green baseline**: `mvn test` passes. If it fails, fix bugs first. Do not refactor on broken code.
2. **Add tests**: Add tests for the code to be changed. If coverage is insufficient, add more cases. Focus on **the parts being changed** and **affected callers**.
3. **Small-step refactoring**: Only one atomic action per step (extract one method / rename one variable / move one function). Run tests immediately after each change.
4. **Commit checkpoint**: `git commit` after each atomic refactoring. Write clear commit messages describing what was done. This enables `git bisect` for locating issues and `git revert` for rollback.
5. **Verify**: Run full test suite + integration tests + manual verification of critical paths. L3/L4 also requires performance baseline comparison.

**Key principle**: Do not make 10 changes then run tests. When something breaks, you cannot pinpoint which step introduced the problem.

## Refactoring Action List

Standard approaches for common refactoring scenarios:

| Scenario | Refactoring Action | Notes |
|---|---|---|
| Method too long | Extract Method | After extraction, method name should express intent; if too many parameters, encapsulate into an object |
| Duplicate code | Extract common method / Template Method | Do not over-abstract; extract only after **3 repetitions** (rule of three) |
| Class too large | Split class / Extract responsibility | First ask "how many responsibilities does this class have"; only split when Single Responsibility is violated |
| Deep nesting | Extract method / Early return / Guard clause | Reduce nesting levels; prefer guard clauses for early return |
| Magic numbers | Extract constant | Constant name should express business meaning, not `NUM_42` |
| Too many parameters | Encapsulate parameter object | Do not create a "parameter bag" anti-pattern (one class holding all parameters) |
| Switch branches | Replace with polymorphism / Strategy pattern | Use switch for fixed branches; use Strategy pattern for extensible branches |
| Global variables | Dependency injection | Reduce static; use constructor injection or method injection |
| Complex conditions | Extract judgment method / Strategy pattern | `if (a && b || c)` -> `if (shouldExecute(a, b, c))` |

## Safe Refactoring Techniques

4 patterns to avoid "code breaks mid-refactoring":

### 1. Strangler Fig Pattern

New code gradually replaces old code; old code is kept until verification is complete, then deleted:

```
// Phase 1: New implementation coexists
public Result process(Input in) {
    if (featureFlag.useNew()) {
        return newProcess(in);   // New implementation
    }
    return oldProcess(in);       // Old implementation kept
}

// Phase 2: After canary verification, traffic switches to new implementation
// Phase 3: Delete old implementation
```

Applicable to: L4 architecture refactoring, replacing entire modules.

### 2. Parallel Implementation + Toggle Switch

New and old implementations coexist, configuration toggle switches between them; instant rollback if problems occur:

```java
if (config.getBoolean("use.new.parser")) {
    return newParser.parse(input);
} else {
    return oldParser.parse(input);
}
```

Applicable to: Switching frameworks, algorithms, or data sources.

### 3. Shadow Traffic

New and old implementations run simultaneously, results are compared, without affecting production:

```java
Result official = oldProcess(input);          // Production uses this
Result shadow = newProcess(input);             // Shadow run, does not affect return
logCompare(official, shadow);                  // Compare, alert on inconsistency
return official;
```

Applicable to: Core path refactoring where direct switchover is not possible.

### 4. Commit Checkpoints

`git commit` after each atomic refactoring, enabling rollback and bisect:

```bash
git commit -m "refactor: extract computeTax method from calculate"
# Run tests, green
git commit -m "refactor: rename price -> unitPrice"
# Run tests, red -> git revert HEAD, go back to previous step
```

Applicable to: All levels of refactoring, **mandatory**.

## Anti-Patterns (Do Not Refactor This Way)

| Anti-Pattern | Problem | Correct Approach |
|---|---|---|
| **Big Bang refactoring** | Change for a week before running tests; cannot locate issues when something breaks | Small steps, commit + test at each step |
| **Opportunistic refactoring** | Tidying up unrelated code while fixing a bug, expanding the change scope | Refactoring and bug fixes in separate commits, separate PRs |
| **Refactoring without tests** | Changing code based on "I understand this code" | Add characterization tests first, then refactor |
| **Refactoring + logic change** | Mixing refactoring with behavior changes, impossible to distinguish | Refactoring does not change behavior; logic changes in separate commits |
| **Cross-branch refactoring** | Multiple branches refactoring simultaneously, merge conflict hell | Complete refactoring in one branch, then cherry-pick |

## Phased Strategy for L3/L4 Refactoring

L3 (interface refactoring) and L4 (architecture refactoring) must be phased, with each phase independently testable and rollbackable:

### L3 Interface Refactoring Example (Change Method Signature)

```
Phase 1: New signature + old signature coexist (old signature delegates to new signature)
Phase 2: All callers migrate to new signature
Phase 3: Delete old signature
```

One commit per phase, run full test suite after each phase.

### L4 Architecture Refactoring Example (Split Microservices)

```
Phase 1: Set up new service, no traffic (independently testable)
Phase 2: Strangler Fig connects to new service, shadow traffic comparison
Phase 3: Canary traffic shift to new service
Phase 4: Decommission old service
```

Phases may be weeks apart; each phase must have a rollback plan.

## Refactoring Completion Acceptance Criteria

When refactoring is done, check:

- [ ] Full test suite passes (including integration tests)
- [ ] No new test failures introduced
- [ ] Behavior unchanged (L1/L2/L3) or behavior changes verified (L4)
- [ ] Code is clearer (readability improved, not more complex)
- [ ] No new code smells introduced
- [ ] No performance regression (L3/L4 run performance baseline comparison)
- [ ] Dead code removed (old implementations, unused imports)

## Usage

1. **Pre-check**: Go through the 5 "pre-refactoring checklist" items before refactoring; if tests are missing, add characterization tests first.
2. **Determine level**: Use the "granularity levels" table to determine L1-L4, and select corresponding process requirements.
3. **Execute process**: Follow the 5 "standard refactoring process" steps; take small steps, commit + test at each step.
4. **Select action**: Use the "refactoring action list" to find the standard approach for the scenario; for code examples, Read `references/refactor-guide/references/refactoring_patterns.md`.
5. **Legacy code**: For old code without tests, Read `refactor-guide/references/legacy_code_patterns.md`, use seam points + characterization tests + dependency breaking.
6. **Real cases**: For end-to-end reference, Read `refactor-guide/references/refactoring_cases.md` (4 cases: Service splitting / static to injection / microservice splitting / add tests then refactor).
7. **Safety strategies**: For L3/L4, use Strangler Fig / parallel implementation / commit checkpoints; avoid big bang.
8. **Acceptance**: Check against "acceptance criteria" to verify refactoring is truly complete.
9. **Do not fabricate when uncertain**: Refactoring techniques are based on "Refactoring" and "Working Effectively with Legacy Code"; this skill does not replace formal methodologies.

## Reference Entries

- **Refactoring Techniques in Detail**: `references/refactoring_patterns.md` (9 techniques, with before/after code comparison)
- **Legacy Code Refactoring**: `references/legacy_code_patterns.md` (seam points, characterization tests, 6 dependency-breaking techniques)
- **Real Refactoring Cases**: `references/refactoring_cases.md` (4 end-to-end cases)
- "Refactoring: Improving the Design of Existing Code" (Martin Fowler)
- "Working Effectively with Legacy Code" (Michael Feathers) -- covers legacy code refactoring, characterization tests
- Project coding standards: `resources/skills/coding-standard/SKILL.md`
- Project JVM troubleshooting: `resources/skills/jvm-troubleshoot/SKILL.md`
