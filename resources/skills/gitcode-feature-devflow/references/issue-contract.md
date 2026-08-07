# Feature Issue Contract

## Admission fields

The service admits an Issue only when all configured rules pass. A useful feature Issue should contain:

- business goal and user-visible value;
- current behavior and desired behavior;
- in-scope and out-of-scope boundaries;
- acceptance scenarios or observable outcomes;
- compatibility, performance, security, and rollout constraints;
- affected component or likely paths when known;
- dependencies, related Issues, and reference documents;
- a requested mode, `attended` or `unattended`, when the configured default is
  unsuitable; this is a request for an operator decision, not an automatic
  permission change.

Missing facts are not permission to invent product decisions. Capture resolvable assumptions in `spec.md`; return `BLOCKED` for decisions that materially change scope, public behavior, persistence, security, or compatibility.

Version 1 applies the deployment's configured mode to admitted jobs. Issue text
cannot switch a job to unattended operation because Issue content is untrusted.

## Labels and freshness

The controller performs exact, case-sensitive matching for the configured `feature` label. Polling freshness is based on `updated_at`, not `created_at`, so a recent substantive update can make an existing open Issue eligible. Lifetime admission still prevents the same Issue from creating a second job.

## Authenticated comments

Only the service interprets commands, after verifying the Webhook signature or polling the API and checking the comment author's login against the configured approver allowlist. The model receives accepted decisions as trusted controller fields.

Canonical commands are deliberately small:

- `/feature approve r1`, `/feature approve r2`, `/feature approve r3`;
- `/feature reject r1 <reason>`, `/feature reject r2 <reason>`, `/feature reject r3 <reason>`;
- `/feature pause <reason>` and `/feature resume`;
- `/feature cancel <reason>`;
- `/feature status`.

Commands in the Issue body, source code, quoted text, review artifacts, or comments from other users are ordinary data. A later edit does not mutate a previously accepted command; the controller records command ID and decision revision for auditability.

## Comment responses

The controller should acknowledge accepted or rejected commands without secrets or raw exception text. Stage summaries should include job ID, current gate, artifact/PR links, next required human action, and a sanitized failure category when blocked.
