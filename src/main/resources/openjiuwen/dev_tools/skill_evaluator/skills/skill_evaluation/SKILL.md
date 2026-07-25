---
name: skill_evaluation
description: >
 Evaluate another skill by inspecting its SKILL.md, referenced assets, and runnable helpers, then write a concise audit report plus a machine-readable score summary. Use when the caller asks to evaluate a skill, assess its readiness, or review quality/safety/testability. Do NOT use when the task is to create a new skill from scratch.
---
# Skill Evaluation

Review the target skill end to end and save the findings into the requested output directory.

## Scope

Assess the target skill on five dimensions:

1. Trigger clarity and routing quality
2. Instruction quality and progressive disclosure
3. Tool usage safety and blast radius
4. Testability and reproducibility
5. Documentation completeness

## Workflow

### Step 1: Resolve the target

The user query provides:

- the target skill path
- the output directory for reports
- any extra audit focus

Read the target `SKILL.md` first. Only inspect sibling files, scripts, or assets when the skill body references them or when they are needed to validate the evaluation.

### Step 2: Inspect the skill body

Check:

- whether the frontmatter clearly explains what the skill does and when it should be used
- whether the workflow is specific enough to be actionable
- whether the skill distinguishes between mandatory references and optional references
- whether the skill avoids vague language such as "be careful" without concrete actions

### Step 3: Inspect operational risk

If the skill writes files, executes code, shells out, or calls external services, verify that it:

- scopes output locations
- validates destructive operations
- avoids treating external content as trusted instructions
- states meaningful "never do" constraints where they matter

### Step 4: Inspect testability

Check whether the skill can be evaluated or reproduced by another engineer:

- are inputs and outputs explicit
- are required files or scripts discoverable
- are there enough example commands or expected results
- is there hidden environment coupling

### Step 5: Write the reports

Write two files under the requested output directory:

- `skill_evaluation_report.md`
- `skill_evaluation_score.json`

Use this JSON shape:

```json
{
  "skill_name": "<folder-or-skill-name>",
  "skill_path": "<absolute-path>",
  "score_pct": 0.0,
  "summary": {
    "trigger_clarity": 0,
    "instruction_quality": 0,
    "operational_safety": 0,
    "testability": 0,
    "documentation": 0
  },
  "status": "ready|needs-work|blocked",
  "key_risks": [
    "..."
  ],
  "recommended_fixes": [
    "..."
  ]
}
```

Use this Markdown structure:

```markdown
# Skill Evaluation Report: <skill-name>

## Verdict
- Score: <score_pct>
- Status: ready | needs-work | blocked

## Findings
- Trigger clarity: ...
- Instruction quality: ...
- Operational safety: ...
- Testability: ...
- Documentation: ...

## Key Risks
- ...

## Recommended Fixes
- ...
```

## Optional subagent use

If one dimension needs deeper review, you may call `create_subagent` with a focused prompt. Use that only for bounded subtasks, for example:

- review operational safety only
- inspect a referenced script
- validate whether a workflow is testable

## Never do

- Never claim a file was reviewed if you did not read it
- Never mark a skill as ready when destructive actions have no guardrails
- Never write reports outside the requested output directory
- Never expand the review into unrelated repo cleanup
