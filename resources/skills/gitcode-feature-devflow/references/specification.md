# Specification Stage

## Inputs

Read the admitted Issue, accepted clarifications, repository instructions, applicable long-term component docs, and the controller-selected component root. Do not create an artifact root until the component root is unambiguous.

## Required specification

Create `spec.md` from `assets/spec-template.md`. It must contain:

- stable work-item identity, mode, owners, and upstream links;
- problem statement, users, value, and measurable success;
- explicit scope and non-goals;
- functional requirements (`FR-*`);
- non-functional requirements (`NFR-*`);
- interface/compatibility requirements (`IFR-*`);
- constraints (`CON-*`), assumptions (`ASM-*`), and exceptions (`EXC-*`);
- acceptance scenarios with Given/When/Then and stable `AC-*` IDs;
- unresolved decisions with an accountable human decision owner.

Requirements must be observable, singular, testable, and free of solution detail unless the solution itself is a constraint. Preserve meaningful upstream wording while removing ambiguity.

## Traceability and resume skeleton

Initialize `traceability.md` from `assets/traceability-template.md`. Give every in-scope FR, NFR, IFR, and testable constraint a row. Do not invent code or test links yet.

Initialize `plan.md` from `assets/plan-template.md` with roots, mode, scope, R1/R2/R3 gate rows, and recovery instructions. Leave implementation tasks empty until R2 passes.

## R1 author check

Before returning `DONE`:

- every acceptance scenario maps to one or more requirements;
- success criteria are measurable with repository-observable evidence;
- exclusions and compatibility expectations are explicit;
- risky assumptions are visible rather than silently treated as fact;
- traceability IDs and paths are internally consistent;
- no implementation, review verdict, or fabricated test result appears.

The next action is independent `REVIEW_R1`; author self-review cannot pass R1.
