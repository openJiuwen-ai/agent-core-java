# Sandbox Provider Profiles

This example area documents the Java-side sandbox provider presets introduced in `extensions.sys_operation`.

## AIO preset

```java
SandboxGatewayConfig config = AioSandboxProfile.config(
    "http://localhost:8080",
    Map.of("timeout_seconds", 30)
);
```

## JiuwenBox preset

```java
SandboxGatewayConfig config = JiuwenBoxSandboxProfile.config(
    "http://localhost:8321",
    Map.of("sandbox_id", "sandbox-001")
);
```

These profiles are thin Java-side adapters over the existing `core.sysop` sandbox gateway config.
