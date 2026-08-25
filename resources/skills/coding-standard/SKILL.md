---
name: coding-standard
description: Compatibility router for Java coding-standard requests. The complete and authoritative rules live in .claude/skills/coding-standard-full and must be loaded explicitly.
---

# Coding Standard compatibility router

This Skill is not the complete Java rule set. Do not use it as rule evidence.

For every Java implementation or review task, explicitly load the authoritative repository Skill at
`.claude/skills/coding-standard-full/SKILL.md`, then load its `rules/*.md` files in the order required by
that Skill and the repository `AGENTS.md`.

When running under Issue Evolver, use the immutable staged Skill named `coding-standard-full`; it is copied
from `.claude/skills/coding-standard-full` at service startup. Never substitute this compatibility router for
the full Skill.
