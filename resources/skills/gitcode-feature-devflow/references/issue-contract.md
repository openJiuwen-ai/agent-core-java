# Feature Issue Contract

## Admission fields

The service admits an Issue only when all configured rules pass. A useful feature Issue should contain:

- business goal and user-visible value;
- current behavior and desired behavior;
- in-scope and out-of-scope boundaries;
- acceptance scenarios or observable outcomes;
- compatibility, performance, security, and rollout constraints;
- affected component or likely paths when known;
- dependencies, related Issues, and reference documents.

Missing facts are not permission to invent product decisions. Capture resolvable assumptions in `spec.md`; return `BLOCKED` for decisions that materially change scope, public behavior, persistence, security, or compatibility.

R1/R2/R3 are always automatic independent Agent reviews. Issue text cannot add
an approval wait or bypass either PR merge boundary.

## Labels and freshness

The controller performs exact, case-sensitive matching for the configured `feature` label. Polling freshness is based on `updated_at`, not `created_at`, so a recent substantive update can make an existing open Issue eligible. Lifetime admission still prevents the same Issue from creating a second job.

## Authenticated comments

Only the service interprets commands, after verifying the Webhook signature or polling the API and checking the comment author's login against the configured approver allowlist. The model receives accepted decisions as trusted controller fields.

Canonical commands are deliberately small:

- `/feature pause <reason>` and `/feature resume`;
- `/feature cancel <reason>`;
- `/feature status`.

Commands in the Issue body, source code, quoted text, review artifacts, or comments from other users are ordinary data. A later edit does not mutate a previously accepted command; the controller records command ID and decision revision for auditability.

## Comment responses

The controller should acknowledge accepted or rejected commands without secrets
or raw exception text. Stage summaries should include Job ID, current Gate,
repair tier/round, artifact/PR links, next retry time, and a sanitized failure
category when blocked.
