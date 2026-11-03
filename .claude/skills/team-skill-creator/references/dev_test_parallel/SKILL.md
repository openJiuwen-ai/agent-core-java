---
name: dev-test-parallel
version: "1.0"
kind: team-skill
roles:
  - id: developer
    purpose: "Implement functional code as described by the user"
    skills: []
    tools: [python3]
  - id: tester
    purpose: "Write test cases for the functional code"
    skills: []
    tools: [python3]
---

# Dev-Test Parallel Team

Two people in parallel: developer implements code, tester writes tests, invisible to each other, leader consolidates both outputs at the end. This is the **minimal example of the Parallel Decomposition pattern**, used as a reference sample when team-skill-creator generates new team skills.

## Workflow

The team follows this workflow (see [workflow.md](workflow.md) for details):

1. **Pre-flight** — Check python3 availability
2. **Parallel Execution** — developer writes code, tester writes tests, invisible to each other
3. **Final Report** — leader consolidates both outputs

## Role Responsibilities

| Role | Responsibility | Output |
| --- | --- | --- |
| developer | Implement functional code | .team/reports/T1_developer.md |
| tester | Write test cases | .team/reports/T2_tester.md |

## File List

- `SKILL.md` — This file, team metadata
- `roles/developer.md` — Developer role definition (with inline persona)
- `roles/tester.md` — Test engineer role definition (with inline persona)
- `workflow.md` — Complete execution script
- `bind.md` — Resource constraints and failure handling
- `dependencies.yaml` — External dependency declarations
