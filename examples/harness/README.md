# Harness Baseline Example

The Java harness baseline currently exposes:

- `DeepAgentConfig`
- `Workspace`
- `HarnessFactory.createDeepAgent`
- `DeepAgent`

This is the first migration layer for the Python `harness` public API. The deeper rails, CLI, LSP, and task-loop internals are still being ported incrementally.

## External tools and rails

Prefer logical `entry_point` resources for extensions supplied by another module. Register the provider with its
concrete resource class before generating or loading the harness configuration:

```java
HarnessConfigBuilder.registerToolProvider(MyTool.class, new HarnessConfigBuilder.HarnessToolProvider() {
    @Override
    public String name() {
        return "my-tool";
    }

    @Override
    public Object create(Path workspaceRoot) {
        return new MyTool(workspaceRoot);
    }
});
```

The class-aware overload lets `generateHarnessConfigYaml` serialize the resource as an `entry_point`. Providers
registered through Java's `ServiceLoader` are also supported when loading a configuration.

Legacy `type: package` resources are not loaded by class name. A host that still uses this format must register an
exact, host-controlled factory before loading the YAML:

```java
HarnessConfigBuilder.registerPackageToolFactory(MyTool.class, MyTool::new);
HarnessConfigBuilder.registerPackageRailFactory(MyRail.class, MyRail::new);
```

Registration is an exact class-name allowlist. A package prefix in YAML does not grant permission to load other
classes from that package. Built-in resources should continue to use `type: builtin`.
