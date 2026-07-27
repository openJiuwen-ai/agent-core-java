# Permissions Baseline

The Java baseline now exposes:

- `PermissionLevel`
- `PermissionCheckResult`
- `PermissionEngine`
- `ToolPermissionHost`
- `PermissionInterruptRail`
- `PermissionFactory`

Suggested verification:

```bash
mvn -Dtest=HarnessPermissionCompatibilityTest test
```
