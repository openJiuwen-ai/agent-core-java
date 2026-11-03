---
description: Credentials, sandbox operations, path validation, shell execution, and SSRF protection security rules.
language: english
paths:
  - "src/main/java/com/openjiuwen/core/security/**"
  - "src/main/java/com/openjiuwen/core/sysop/**"
  - "src/main/java/com/openjiuwen/core/common/security/**"
  - "src/main/java/com/openjiuwen/extensions/**"
  - "src/main/java/com/openjiuwen/harness/security/**"
  - "src/main/java/com/openjiuwen/harness/rails/security/**"
alwaysApply: false
---

# Security Rules

## Credential Handling

- Never hard-code secrets, API keys, tokens, or real endpoints in source files.
- All credentials must come from environment variables or config loaded at runtime.
- Use `System.getenv("KEY")` with a safe default for tests.
- In tests, use `System.getenv(..., "mock-api-key")` or mock the config — never real credentials.
- API keys in example config files (`apiconfig.json`, `apiconfig_example.json`) must use placeholder values, not real keys.

## .env Files

- `.env` and `.env.*` files must not be committed.
- These are already gitignored; do not override that.
- `.env.example` files are allowed and should contain only placeholder values.

## Sandbox Operations

- `core/sysop/` handles shell and filesystem operations.
- Sandboxed operations (`core/sysop/sandbox/`) enforce path scoping and guardrails.
- Never bypass path validation or approval flows in sandbox code.
- Preserve interrupt/confirm semantics for user-facing operations.
- `SandboxGateway` is the singleton entry point; all sandbox access must go through it.

## File Path Validation

- Always validate user-supplied file paths before operations.
- Use `PathChecker` in `com.openjiuwen.core.common.security` for sensitive-path checks.
- Use `Path.normalize()` + `toAbsolutePath()` before comparing against allowed scopes.
- Reject paths containing `..` that escape the allowed root.
- `LocalFsOperation.isWithinRoot()` checks that resolved paths do not escape the sandbox root via `!root.relativize(candidate).startsWith("..")`.
- On resolution failure, default to **deny** (fail-closed). `PathChecker.checkSensitive()` returns `true` (blocks access) when normalization throws.

## Shell Execution

- Never construct shell commands from unsanitized user input.
- Use `ProcessBuilder` with list arguments — never `Runtime.exec()` with concatenated strings.
- `LocalShellOperation` applies two security filters before every execution:
  1. `checkAllowlist()` — first token of command must be in the configured allowlist.
  2. `checkDangerousPatterns()` — regex pattern matching against configured dangerous patterns.
- Do not bypass or weaken these filters without a security review.

## SSRF Protection

- `UrlUtils.checkUrlIsValid()` blocks internal IPs (loopback, site-local, link-local, any-local).
- Can be disabled via `SSRF_PROTECT_ENABLED=false` env var — do not disable in production.
- All HTTP client creation must go through `UrlUtils` validation when user-supplied URLs are involved.

## SSL/TLS

- Use `SslUtils.createStrictSslContext()` for production connections — enforces TLS 1.2+ with strong cipher suites.
- Certificate paths must be within `SAFE_CERT_DIR`; file size capped at 1 MB.
- `SslUtils.createInsecureSslContext()` (trusts all certs) is only for explicit `verify=false` scenarios — never use as default.

## Third-Party Dependencies

- Do not add dependencies without reviewing `pom.xml` and understanding the security implications.
- New network-facing dependencies require review.
- Check for known CVEs when upgrading dependencies (OWASP Dependency-Check or similar).

## Security-Sensitive Areas

- `core/common/security/` — path checking, SSRF protection, SSL, proxy, JSON safety
- `core/sysop/` — shell, filesystem, sandbox operations
- `harness/security/` — permission engine (ALLOW/ASK/DENY policy per tool)
- `harness/rails/security/` — security rails (read-only mode enforcement)
- `extensions/sys_operation/sandbox/` — isolation and provider code

Changes to these areas require extra review and testing.

## Guardrail and Permission Layers

The project implements a layered security model. Understand the layer before making changes:

| Layer | Package | Purpose |
|---|---|---|
| Guardrail | `core.security.guardrail` | User input risk detection (SAFE/LOW/MEDIUM/HIGH/CRITICAL) |
| Permission | `harness.security` | ALLOW/ASK/DENY policy per tool via `PermissionEngine` |
| Security Rail | `harness.rails.security` | Read-only mode enforcement, blocks write tools |
| SysOp Validation | `core.sysop` | Shell allowlisting, dangerous pattern blocking, path traversal prevention |
| Common Security | `core.common.security` | SSRF, SSL, path sensitivity, proxy credential handling |