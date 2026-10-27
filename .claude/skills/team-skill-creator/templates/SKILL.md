---
name: <team-skill-name>          # Team skill name, kebab-case, matches directory name
version: "1.0"                   # Version number
kind: team-skill                 # Fixed value, identifies this as a team skill
roles:                           # Role overview, leader reads this during ReAct for orchestration
  - id: <role-1>                 # Role id, kebab-case, matches roles/<role-1>.md filename
    purpose: "<one-sentence responsibility>"       # What this role does
    skills: [<skill-name>]       # External skills this role depends on; empty array means pure reasoning
    tools: [<tool-name>]         # Tools this role needs, e.g., python3/curl/jq
  - id: <role-2>
    purpose: "<one-sentence responsibility>"
    skills: []
    tools: []
---

# <Team Name>

<2-3 sentences describing what the team does, what collaboration pattern it uses, and what problem it solves.>

## Workflow

The team follows this workflow (see [workflow.md](workflow.md) for details):

1. **<Step Name>** — <One sentence>
2. **<Step Name>** — <One sentence>
...

## Role Responsibilities

| Role | Responsibility | Output |
| --- | --- | --- |
| <role-1> | <responsibility> | <output file or content> |
| <role-2> | <responsibility> | <output file or content> |

## File List

- `SKILL.md` — This file, team metadata
- `roles/*.md` — Role definitions (with inline persona)
- `workflow.md` — Complete execution script
- `bind.md` — Resource constraints and failure handling
- `dependencies.yaml` — External dependency declarations
