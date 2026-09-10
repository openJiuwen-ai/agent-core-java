# 工具护栏 Tool Guardrail

工具护栏是 DeepAgent 的安全子系统，负责在**每次工具调用前**进行权限检查，根据三态决策（ALLOW / ASK / DENY）决定是否放行、是否需要用户确认、或直接拒绝。它不是独立的安全平台，而是一层建立在 `PermissionInterruptRail`（Agent Rail 链上的拦截器）之上的工具级访问控制机制。

与 `安全护栏Guardrail.md` 讨论的内容风险护栏（`com.openjiuwen.core.security.guardrail`）不同，工具护栏关注的是**工具调用的参数和路径安全**：bash 命令是否危险、文件读写是否越权、是否涉及网络外泄等。

## 能力定位

- 工具护栏的核心职责是在工具执行前拦截，按权限策略决定 ALLOW / ASK / DENY。
- 策略评估由 `PermissionEngine`（多管线最严合并）完成，Rail 只负责调度和执行决策。
- 支持三个层次的控制：命令参数级（Pipeline A — `TieredPolicy`）、文件路径级（Pipeline B — `FileGuardChecker`）、内置安全底线（builtin_rules.yaml）。
- 支持 ASK 确认回调机制，用户可以在运行时批准或拒绝工具调用，并选择会话级记忆或持久化放行。

## 三态决策语义

| 级别     | 语义       | Rail 行为                                                    |
| -------- | ---------- | ----------------------------------------------------------- |
| `ALLOW`  | 允许执行    | 正常执行工具                                     |
| `ASK`    | 需要用户确认 | 中断流程，等待用户确认后恢复                |
| `DENY`   | 拒绝执行    | 跳过工具，返回 `[PERMISSION_DENIED] ...` 拒绝消息给 LLM |

多管线 `strictest` 合并规则：任一管线返回 `DENY` → `DENY`；任一返回 `ASK`（且无 `DENY`）→ `ASK`；否则 → `ALLOW`。

## 核心类型

所有类型位于扁平包 `com.openjiuwen.harness.security`（rail 在 `com.openjiuwen.harness.rails.security`）。

| 类型                       | 作用                                         | 何时使用                              |
| ------------------------ | -------------------------------------------- | ----------------------------------- |
| `DeepAgentConfig`        | Agent 配置，包含 `permissions` 和 `permissionHost` | 创建 Agent 时传入权限配置             |
| `HarnessConfig`          | 声明式 harness 配置，含 `permissions` 段 | 通过 YAML 声明权限配置时 |
| `PermissionEngine`       | 多管线权限引擎，持有 config 与 FileGuardChecker     | 理解权限评估逻辑时                   |
| `PermissionInterruptRail` | 工具调用拦截 Rail，执行 ALLOW/ASK/DENY 决策       | 理解 Rail 链拦截机制时                |
| `ToolPermissionHost`     | ASK 确认宿主边界，管理确认/场景/落盘 hook | 需要自定义确认流程时                 |
| `PermissionConfirmResponse` | 用户确认响应（approved / feedback / autoConfirm / persistAllow） | 实现 ASK 回调时                   |
| `TieredPolicy`           | Pipeline A — 参数级规则评估                    | 理解命令 pattern 匹配时             |
| `FileGuardChecker`       | Pipeline B — 路径级规则评估                    | 理解文件路径保护时                  |
| `PathAccessExtractor`    | 从工具调用抽取 `(path, action)` 访问集 | 理解路径抽取逻辑时 |
| `FileGuardConfigNormalizer` | 编译 `file_guard` / `external_directory` 配置 | 理解配置编译时 |
| `GlobMatcher`            | `**` 跨目录 glob 匹配器 | 理解 glob 规则匹配时 |
| `PermissionPatterns`     | 通配/路径匹配与 YAML 持久化写入（原子写） | 理解 persist-allow 机制时           |

## 接入步骤

### 1. 构建 permissions 配置

工具护栏的配置是一个 `Map<String, Object>`，通过 `DeepAgentConfig.builder().permissions(permissions)` 传入：

```java
Map<String, Object> permissions = new LinkedHashMap<>();
permissions.put("enabled", true);                           // 总开关
permissions.put("schema", "tiered_policy");                 // 策略模式
permissions.put("permission_mode", "normal");               // normal | strict

// 工具级基线：未匹配任何规则时的默认决策
permissions.put("tools", Map.of(
    "bash", "ask",          // bash 命令默认需要确认
    "write_file", "deny"    // 写文件默认拒绝
));
permissions.put("defaults", Map.of("*", "allow"));          // 全局默认放行

// 参数级规则：按 pattern 匹配命令参数
permissions.put("rules", List.of(
    Map.of("id", "curl_deny", "tools", List.of("bash"),
            "pattern", "curl *", "action", "deny"),
    Map.of("id", "cat_allow", "tools", List.of("bash"),
            "pattern", "cat *", "action", "allow")
));

// 运行时持久化的放行规则（初始为空）
permissions.put("approval_overrides", List.of());

// 文件路径级规则（可选）
Map<String, Object> fileGuard = new LinkedHashMap<>();
fileGuard.put("enabled", true);
fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
fileGuard.put("paths", List.of(Map.of(
    "path", "/etc/hosts",
    "read", "allow", "write", "deny", "exec", "deny",
    "match", "prefix"          // prefix | glob
)));
permissions.put("file_guard", fileGuard);
```

> **注意**：`enabled` 必须显式设为 `true`，否则 `DeepAgent.ensureInitialized()` 不会注册权限 Rail，工具护栏完全不生效。

### 2. 创建 ToolPermissionHost（可选，用于 ASK 确认）

如果配置了 `bash: ask` 等需要确认的工具，需要提供一个 `ToolPermissionHost` 来处理确认请求：

```java
ToolPermissionHost host = new ToolPermissionHost();
host.setWorkspaceDirResolver(() -> workspacePath);
host.setPermissionYamlPath(yamlPath);   // persist_allow 落盘目标

// 设置确认回调（异步 hook，返回 CompletionStage）
host.setRequestPermissionConfirmationHook(request -> {
    System.out.println("确认请求: result=" + request.result().getReason());
    // 返回确认结果
    return CompletableFuture.completedFuture(
        new ToolPermissionHost.PermissionConfirmResponseWrapper(
            new PermissionConfirmResponse(
                true,    // approved：是否批准
                "",      // feedback：拒绝时的反馈信息
                true,    // autoConfirm：本次会话内记住（相同 key 不再询问）
                false    // persistAllow：永久落盘到 permissions YAML
            )));
});
```

如果未设置回调，ASK 决策会走内置中断/恢复路径（interrupt/resume），Agent 会暂停等待外部恢复。

### 3. 构建 DeepAgent 并注册工具

```java
DeepAgentConfig config = DeepAgentConfig.builder()
    .enableTaskLoop(true)                  // 必须开启，否则 ReAct 循环不会运行
    .systemPrompt("你是一个助手，根据用户请求调用工具。")
    .maxIterations(8)
    .completionTimeout(120.0)
    .language("cn")
    .model(modelMap)
    .workspacePath(workspacePath.toString())
    .permissions(permissions)              // 传入权限配置
    .permissionHost(host)                  // 传入确认宿主
    .build();

DeepAgent agent = HarnessFactory.createDeepAgent(card, config, ws);
agent.registerHarnessTool(buildBashTool());
agent.registerHarnessTool(buildReadFileTool());
agent.ensureInitialized();   // 此处会自动注册 PermissionInterruptRail
```

### 4. 声明式入口（HarnessConfig YAML）

也可以在 harness 配置 YAML 中直接声明 `permissions` 段，`HarnessConfigBuilder` 会把它投影到 `DeepAgentConfig`：

```yaml
schema_version: harness_config.v0.1
id: my-agent
name: My Agent
permissions:
  enabled: true
  schema: tiered_policy
  tools:
    bash: ask
  file_guard:
    enabled: true
    defaults: { read: ask, write: ask, exec: ask }
    paths:
      - path: /etc/hosts
        read: allow
        write: deny
        exec: deny
        match: prefix
```

## 权限配置详解

### 配置层级总览

```
permissions: Map<String, Object>
├── enabled: true/false              ← 总开关
├── schema: "tiered_policy"          ← 策略模式
├── permission_mode: normal/strict   ← 严格模式
├── tools: { bash: allow/ask/deny }  ← 工具级基线
├── defaults: { "*": allow }         ← 全局默认
├── rules: [...]                     ← 参数级 pattern 规则
├── approval_overrides: [...]        ← 运行时持久化的放行
└── file_guard:                      ← 路径级规则（可选）
    ├── enabled: true/false
    ├── defaults: {read, write, exec}
    └── paths: [{path, read, write, exec, match}]
```

### 规则优先级链（Pipeline A）

规则从高到低依次评估，任何一层的 DENY 会短路返回：

```
1. baseline 短路     tools.<toolName> = deny → 直接返回 DENY
2. 内置 CRITICAL 规则  builtin_rules.yaml (10 条)
3. 用户 rules        permissions.rules[]
4. approval_overrides 运行时持久化的 allow → ALLOW
5. baseline level    tools.<toolName> 的 allow/ask
6. defaults.*        全局默认
7. fallback          以上都不匹配 → ASK (fail-closed)
```

### permission_mode: normal 与 strict

`permission_mode` 只影响使用 `severity` 字段（而非 `action` 字段）的规则：

| Severity | normal 模式 | strict 模式 |
| -------- | --------- | --------- |
| LOW      | ALLOW     | ALLOW     |
| MEDIUM   | ALLOW     | ASK       |
| HIGH     | ASK       | ASK       |
| CRITICAL | ASK       | DENY      |

内置规则中 9 条使用 `severity: CRITICAL`，1 条使用 `action: deny`。因此：

- normal 模式：`rm -rf /` 等危险命令需要用户确认（ASK）
- strict 模式：直接拒绝（DENY），不给确认机会

带 `action` 字段的规则在两种模式下行为一致。

### 默认行为

| 场景                               | 行为                           |
| -------------------------------- | ---------------------------- |
| 不传 permissions                   | 护栏不生效，所有工具直接放行              |
| 传了 permissions 但 `enabled` 缺失    | DeepAgent 不注册 Rail（护栏不生效）   |
| PermissionEngine 内部 `enabled` 缺失 | 默认 `true`（但需通过 DeepAgent 门禁）|
| 无规则匹配时 | 默认 ASK（fail-closed） |
| FileGuard 未配置时 | Pipeline B 跳过（null） |
| FileGuard 默认轴（read/write/exec） | ASK |

### 内置安全规则

内置规则文件位于 `src/main/resources/com/openjiuwen/harness/security/builtin_rules.yaml`，随 jar 打包，从 classpath 加载，**进程级单例缓存**，首次读取后不可变。

| 规则 | 风险 | 说明 |
|------|------|------|
| `shell_fs_recursive_or_forced_delete` | CRITICAL | `rm -rf /`、`find . -delete`、`shred` |
| `shell_disk_partition_or_raw_device_write` | CRITICAL | `mkfs`、`fdisk`、`dd of=/dev/` |
| `shell_download_and_execute` | CRITICAL | `curl \| bash`、`iwr \| iex` |
| `shell_obfuscated_or_dynamic_execution` | CRITICAL | `base64 -d \| bash`、`eval`、`python -c` |
| `shell_reverse_shell_or_bind_shell` | CRITICAL | `/dev/tcp/`、`nc -e`、`socat EXEC:` |
| `shell_privilege_escalation` | CRITICAL | `sudo`、`su root`、`runas`、`psexec` |
| `shell_data_exfiltration` | CRITICAL | `curl --upload-file`、`scp`、`rsync` |
| `shell_remote_execution_or_lateral_movement` | CRITICAL | `Invoke-Command`、`ssh`、`wmic` |
| `shell_fork_bomb_or_resource_abuse` | CRITICAL | `:()\{:\|:&\};:`、`kill -9 -1` |
| `shell_system_shutdown_or_reboot` | `action: deny` | `shutdown`、`reboot`、`init 0` |

> 内置规则的设计意图是"安全底线不可绕过"——即使用户配置了 `bash: allow`，`rm -rf /` 仍然会被内置规则拦下。无法通过用户规则覆盖内置规则，只能通过修改源码 YAML 或 `enabled: false` 禁用整个护栏。

## ASK 确认

### 四态确认模型

```java
new PermissionConfirmResponse(
    true,    // approved：是否批准
    "",      // feedback：拒绝原因（approved=false 时返回给 LLM）
    true,    // autoConfirm：本次会话记住（相同 autoConfirmKey 不再询问）
    false    // persistAllow：永久写入 permissions YAML
);
```

| approved | autoConfirm | persistAllow | 行为                                    |
| -------- | ----------- | ------------ | ------------------------------------- |
| true     | false       | false        | 允许本次调用                                |
| true     | true        | false        | 会话内 auto-confirm 相同 key 的调用          |
| true     | -           | true         | 落盘永久放行（写回 agent YAML 的 permissions 段） |
| false    | -           | -            | 拒绝，返回 feedback 给 LLM                  |

### autoConfirmKey 生成规则

- **shell 工具**：按解析后的简单子命令文本生成（如 `bash:curl http://x`）。
- **非 shell 工具**：直接用 toolName（如 `read_file`）。
- **有风险结构**（管道、重定向、heredoc 等）：key 为空串，强制每次确认（fail-closed）。

### 持久化写入

`persistAllow=true` 时的落盘流程：

1. `PermissionPatterns.mergePermissionAllowRuleIntoPermissions()` 合并 allow 规则到当前配置副本（shell 命令 → `approval_overrides`，非 shell → `tools.<name>=allow`）。
2. 优先调用 `ToolPermissionHost` 的 `PersistAllowRuleHook`；未设置时经 `PermissionPatterns.writePermissionsSectionToAgentConfigYaml()` 写入 `permissionYamlPath`（临时文件 + 原子移动，失败不会留下半写文件）。
3. 落盘成功后 rail 刷新内存配置；落盘失败回滚内存配置并退化为会话级记忆。

路径级放行可经 `PermissionPatterns.mergeFileGuardAccessAllows()` 合并进 `file_guard.paths`（write⇒read+write allow，exec⇒read+exec allow，已有条目只升不降）。

## 运行时修改

### 会话级自动确认

通过 ASK 确认回调返回 `approved=true, autoConfirm=true, persistAllow=false`，存入会话状态，Agent 关闭后失效。

### 手动代码调用

如果持有 `PermissionEngine` 引用，可以直接调用：

```java
engine.updateConfig(newPermissionsMap);  // 替换内存配置
```

`updateConfig` 会重建 `ExternalDirectoryChecker` 与 `FileGuardChecker`（重新编译 file_guard 规则）。

## file_guard 路径保护

file_guard 是 Pipeline B，对文件工具（`read_file`、`write_file`、`edit_file` 等）和 shell 命令中的文件路径进行路径级权限控制。

### 配置示例

```java
Map<String, Object> fileGuard = new LinkedHashMap<>();
fileGuard.put("enabled", true);
fileGuard.put("defaults", Map.of(
    "read", "allow",    // 默认允许读
    "write", "allow",   // 默认允许写
    "exec", "ask"       // 默认执行需确认
));
fileGuard.put("paths", List.of(
    Map.of(
        "path", "/etc/hosts",      // 受保护路径
        "read", "allow",           // 允许读
        "write", "deny",           // 拒绝写
        "exec", "deny",            // 拒绝执行
        "match", "prefix"          // 匹配方式: prefix | glob
    ),
    Map.of(
        "path", "**/.env*",        // glob 匹配所有 .env 文件
        "read", "deny",
        "write", "deny",
        "exec", "deny",
        "match", "glob"
    )
));
```

### 路径匹配方式

| match | 说明 | 示例 |
|-------|------|------|
| `prefix` | 最长前缀匹配 | path=`/etc` 匹配 `/etc/hosts`、`/etc/passwd` |
| `glob` | Glob 通配符匹配（`**` 跨目录，`*` 不跨目录） | path=`**/.env*` 匹配 `/work/.env.local` |

### 轴蕴含规则

- `WRITE` 操作同时检查 `READ` 轴（write ⇒ read 蕴含）。
- `EXEC` 操作同时检查 `READ` 轴（exec ⇒ read 蕴含）。
- 取 `strictest(action_level, read_level)` 作为最终决策。

### 旧配置迁移

`external_directory` 旧配置会被自动迁移吸收：`"*": ask` 投影为 file_guard 的 per-axis defaults，具名路径键投影为 prefix 规则（`allow` 动作投影为 read/write=allow + exec=ask）。未配置 `file_guard` 时，legacy 分支会隐式放行 workspace 根目录。

## 跨平台注意事项

### Windows 路径问题

在 Windows 上，`/etc/hosts` 不是绝对路径（无盘符），`PathAccessExtractor` 会将其解析为 `workspace.resolve("/etc/hosts")` → `<当前盘符>:/etc/hosts`，这与 file_guard 规则中的 `/etc/hosts` 不匹配，导致回退到默认值。

**解决方案**：使用 workspace 相对路径或带盘符的绝对路径，在配置和工具调用中使用同一路径。

### enableTaskLoop 必须开启

`DeepAgentConfig.enableTaskLoop(true)` 是 ReAct 循环运行的前提。如果设为 `false`，`DeepAgent.invokeInternal()` 会短路返回元数据，根本不会调用 `ReActAgent.invoke()`，工具护栏不会被触发。

## 当前实现边界

- 工具护栏拦截所有工具调用（`intercept=all_tools`），不限于 `permissions.tools` 中列出的工具。
- Shell 工具名归一化：`mcp_exec_command`、`create_terminal` 统一为 `bash`。
- Shell AST 当前使用保守扫描器（纯字符串扫描），结构上预留了升级空间。
- 内置规则不可通过用户规则覆盖，只能通过修改源码 YAML 或 `enabled: false` 禁用整个护栏。

## 参考入口

- 引擎源码：`src/main/java/com/openjiuwen/harness/security/PermissionEngine.java`
- Rail 源码：`src/main/java/com/openjiuwen/harness/rails/security/PermissionInterruptRail.java`
- 内置规则：`src/main/resources/com/openjiuwen/harness/security/builtin_rules.yaml`
- 内容风险护栏（不同子系统）：`安全护栏Guardrail.md`
