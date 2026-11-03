---
name: team-skill-creator
description: Team Skill Generator. Crystallizes a multi-agent collaboration pattern into a reusable team skill file set (SKILL.md / roles/*.md / workflow.md / bind.md / dependencies.yaml). Automatically applied when a user wants to turn a collaboration pattern into a template, create a team skill, write a specific team skill file, or reference investment-analysis-team to build a new team skill. Related keywords: team skill creator, team skill creation, crystallize collaboration, team skill template, roles/workflow/bind/dependencies. Not applicable to: single-agent problems, Java code assembly (use agent-team-guide), discussions unrelated to team skill files.
---

# Team Skill Generator

This skill guides Claude in generating a complete team skill file set for the user. A team skill is a reusable collaboration template for the `com.openjiuwen.agentteams` framework, consisting of 5 types of files placed in the team workspace `skills/` or `~/.openjiuwen/workspace/skills/` for automatic discovery by the framework.

## Generation Process (Must Follow This Order)

1. **Gather Requirements**: Ask the user four questions (if not fully provided):
   - What does the team do? (One-sentence goal)
   - How many roles? What is each role called and what does it do?
   - How do roles collaborate? (Parallel / Debate / Pipeline / Quality Gate / Hybrid)
   - What external skills / tools does each role depend on?
2. **Select Collaboration Pattern**: Choose 1-3 pattern combinations from "Collaboration Pattern Classification" based on the user's description.
3. **Create Directory**: `<team-skill-name>/` with a `roles/` subdirectory.
4. **Generate 5 File Types from Templates**: Fill in the templates below.
5. **Focus Check on roles/*.md**: Each role file must contain an `## Inline Persona for Teammate` section (the framework does not auto-load it; the leader must read it and paste it into the dispatch prompt).
6. **Self-Check**: Verify each item in the "Post-Generation Self-Check List".
7. **Inform Placement Path**: Tell the user to place the generated directory under `~/.openjiuwen/workspace/skills/` or the team workspace `skills/` for the framework to discover it.

## Five File Templates (With Field Annotations)

When generating a team skill, use `read_file` to read the corresponding template file, fill in the placeholders, and `write_file` to the target directory. Template files contain complete field annotations and are not inlined in this SKILL.md to save context.

| File to Generate | Read Template | Description |
| --- | --- | --- |
| `<team-skill-name>/SKILL.md` | `templates/SKILL.md` | Team metadata: name/version/kind: team-skill/roles overview |
| `<team-skill-name>/roles/<role>.md` | `templates/role.md` | Role definition: Identity/Success Criteria/Boundary/Output Schema/Inline Persona |
| `<team-skill-name>/workflow.md` | `templates/workflow.md` | Execution script: flowchart + Step 0~N + Final Report |
| `<team-skill-name>/bind.md` | `templates/bind.md` | Constraints: Resource Constraints/Behavioral Constraints/Failure Handling |
| `<team-skill-name>/dependencies.yaml` | `templates/dependencies.yaml` | Dependencies: skills/tools/global_dependencies |

**Key Reminder**: The `## Inline Persona for Teammate` section in `roles/*.md` is what the leader must `read_file` and paste into the dispatch prompt before `spawn_member` -- the framework does not auto-load it; the leader must explicitly read it. This section in the template is wrapped with ` ``` ` (not ` ```markdown `) to avoid nested code block errors when the leader extracts it.

## Collaboration Pattern Classification (Choose 1-3 Combinations)

Select patterns based on the user's description, which determines the workflow structure and bind constraints:

| Pattern | Characteristics | Workflow Structure | Key Bind Constraints |
|---|---|---|---|
| **Parallel Decomposition** | Multiple roles analyze different dimensions simultaneously, invisible to each other | Parallel spawn → each outputs report → leader consolidates | `max_parallel_teammates` / analysts invisible to each other |
| **Adversarial Debate** | Two opposing roles exchange views directly point-to-point | Round 1 independent → Round 2 direct debate → output conclusion | `debate_rounds_limit` / `min_debate_rounds` / direct point-to-point > leader forwarding |
| **Pipeline** | Upstream output is automatically delivered to downstream (via dependencies) | T1 → T2 → T3 with dependencies | Use `create_task(dependencies=[...])` / `member_results_delivery` for auto-delivery |
| **Quality Gate** | Leader verifies quality after upstream completes before downstream starts | Upstream → Quality check → Downstream | Quality gate failure loops back / `max_retry_per_step` |
| **Hybrid** | Combination of the above (e.g., investment-analysis-team = parallel + debate + pipeline) | Multi-Step combination | Multiple constraints, write visibility rules per phase |

**Pattern Selection Tips**:
- User says "several roles analyze different aspects simultaneously" → Parallel Decomposition
- User says "pro vs con / optimistic vs pessimistic / support vs oppose" → Adversarial Debate
- User says "upstream finishes then downstream continues" → Pipeline
- User says "must check quality before continuing" → Quality Gate
- User says "both parallel and debate" → Hybrid

## Minimal Complete Example (2-Role Parallel)

User says "I want a team: one writes code, one writes tests, both work in parallel." The generated minimal team skill:

### `<team-skill-name>/SKILL.md`

```yaml
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

Two people in parallel: developer implements code, tester writes tests, invisible to each other, leader consolidates both outputs at the end.

## Workflow

1. **Pre-flight** — Check python3 availability
2. **Parallel Execution** — developer writes code, tester writes tests
3. **Final Report** — leader consolidates both outputs

## Role Responsibilities

| Role | Responsibility | Output |
| --- | --- | --- |
| developer | Implement functional code | .team/reports/T1_developer.md |
| tester | Write test cases | .team/reports/T2_tester.md |
```

### `<team-skill-name>/roles/developer.md`

```markdown
# Role: Developer

## Identity

> *"I am the feature implementer, writing runnable code based on the user's description."*

Responsible for translating user requirements into code implementations.

## Success Criteria

- Code meets all functional points described by the user
- Code runs without syntax errors

## Boundary

**Forbidden**:
- Do NOT write tests (this is the tester's responsibility)

**Mandatory**:
- You MUST output complete runnable code

## Output Schema

\`\`\`markdown
## Role: Developer

### Feature Implementation
<code block>

### Implementation Notes
<key decision points>
\`\`\`

## Inline Persona for Teammate

\`\`\`
ROLE: Developer in a Teamskill.

You are the feature implementer, writing runnable code based on the user's description.

You MUST output complete runnable code.
You MUST NOT write tests.

INPUTS YOU WILL RECEIVE:
- Feature description: {FEATURE_DESC_PLACEHOLDER}

PROCESSING INSTRUCTIONS:
1. Analyze the feature description
2. Implement the code
3. Write to .team/reports/T1_developer.md
4. send_message to leader with file path and summary

OUTPUT:
- Write to file: .team/reports/T1_developer.md
\`\`\`
```

### `<team-skill-name>/roles/tester.md`

Full version see `references/dev_test_parallel/roles/tester.md` (persona changed to "I am the test engineer, writing test cases for the functional code", forbidden to "implement functional code").

### `<team-skill-name>/workflow.md`

```markdown
# Workflow: Dev-Test Parallel Team

## Flowchart

\`\`\`mermaid
graph TD
    S0[Step 0 Pre-flight] --> S1[Step 1 Parallel Execution]
    S1 --> Final[Final Report]
\`\`\`

## Step 0: Pre-flight

- **Executor**: leader
- **Action**: Check python3 availability
- **Quality Gate**: python3 available

## Step 1: Parallel Execution

- **Executor**: developer + tester (parallel)
- **Input**: User feature description
- **Output**: .team/reports/T1_developer.md + .team/reports/T2_tester.md
- **Quality Gate**: Both files exist

## Final Report

leader consolidates into .team/reports/T3_final_report.md, quoting both outputs verbatim.
```

### `<team-skill-name>/bind.md`

```markdown
# Bind: Dev-Test Parallel Team Constraints

## Resource Constraints

| Constraint | Limit | Description |
|---|---|---|
| `max_parallel_teammates` | 2 | Two people in parallel |
| `total_wall_clock_budget` | 10 minutes | Maximum execution time |
| `per_role_token_limit` | 8000 tokens | Per-person token limit |

## Behavioral Constraints

1. **Leader does not generate content**: Leader is only responsible for task distribution and report consolidation
2. **developer and tester are invisible to each other**: Cannot see each other's output during parallel work

## Failure Handling

| Failure Scenario | Handling |
|---|---|
| One party fails | Retry 2 times; if still fails, leader reports the failure point |
```

### `<team-skill-name>/dependencies.yaml`

```yaml
# Dependencies: Dev-Test Parallel Team

skills: []

tools:
  - name: python3
    source: local
    required: true
    purpose: "Run code and tests"
    roles:
      - developer
      - tester

global_dependencies:
  description: "Only python3 needed"
  fallback_mode: "Cannot run without python3"
```

## Post-Generation Self-Check List (Required)

After generating the 5 file types, verify each item:

- [ ] `SKILL.md` frontmatter contains `kind: team-skill` + `roles` overview
- [ ] Each `.md` filename under `roles/` matches the `id` in `SKILL.md`
- [ ] Each `roles/*.md` contains all five sections: `## Identity` / `## Success Criteria` / `## Boundary` / `## Output Schema` / `## Inline Persona for Teammate`
- [ ] `## Inline Persona for Teammate` section is wrapped with ` ``` ` (not nested markdown code block), containing `ROLE:` / `You MUST` / `INPUTS YOU WILL RECEIVE` / `PROCESSING INSTRUCTIONS` / `OUTPUT` sub-sections
- [ ] `workflow.md` has Step numbering + `Executor` / `Input` / `Output` / `Quality Gate` for each step
- [ ] `bind.md` contains all three sections: `Resource Constraints` / `Behavioral Constraints` / `Failure Handling`
- [ ] `dependencies.yaml` items with `required: false` can degrade gracefully when missing
- [ ] Output file paths consistently use `.team/reports/T<sequence>_<role>_<output>.md` format
- [ ] Inter-role visibility rules match the collaboration pattern (parallel = invisible to each other, debate Round 2 = directly visible)
- [ ] Inform user of placement path: `~/.openjiuwen/workspace/skills/` or team workspace `skills/`

## Usage

1. **Gather Requirements**: Proactively ask when the user hasn't provided all 4 questions.
2. **Select Pattern**: Choose 1-3 combinations from "Collaboration Pattern Classification".
3. **Apply Templates**: All 5 file type templates are in this skill; fill in the blanks per annotations.
4. **Reference Minimal Example**: The complete 2-role parallel example is above; expand for complex scenarios following the same pattern.
5. **Self-Check**: Verify each item in the "Post-Generation Self-Check List", especially the `## Inline Persona for Teammate` section.
6. **Inform Placement**: After generation, tell the user to place the directory under `~/.openjiuwen/workspace/skills/` or the team workspace `skills/`.

## Reference Entry Points

- Complete team skill example: `examples/agent_teams/skills/investment-analysis-team/` (8-role hybrid pattern)
- **Minimal complete reference sample**: `references/dev_test_parallel/` (2-role parallel, all 5 files in full version; read this directory for comparison when generating new team skills)
- Framework usage guide: `resources/skills/agent-team-guide/SKILL.md`
- Full documentation: `documents/zh/2.开发指南/多智能体/AgentTeams.md` "Team Skill" section
