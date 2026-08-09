# Permissions Baseline

The Java baseline now exposes:

- `PermissionLevel`
- `PermissionResult`
- `PermissionEngine`
- `ToolPermissionHost`
- `PermissionInterruptRail`
- `PermissionFactory`

Suggested verification:

```bash
mvn -Dtest=HarnessPermissionCompatibilityTest test
```
