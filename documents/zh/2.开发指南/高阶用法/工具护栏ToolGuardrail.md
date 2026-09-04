# 工具护栏 Tool Guardrail

工具护栏是 DeepAgent 的安全子系统，负责在 **每次工具调用前** 进行权限检查，根据三态决策（ALLOW / ASK / DENY）决定是否放行、是否需要用户确认、或直接拒绝。它不是独立的安全平台，而是一层建立在 `PermissionInterruptRail`（Agent Rail 链上的拦截器）之上的工具级访问控制机制。

与 `安全护栏Guardrail.md` 讨论的内容风险护栏（`com.openjiuwen.core.security.guardrail`）不同，工具护栏关注的是**工具调用的参数和路径安全**：bash 命令是否危险、文件读写是否越权、是否涉及网络外泄等。

## 能力定位

- 工具护栏的核心职责是在工具执行前拦截，按权限策略决定 ALLOW / ASK / DENY。
- 策略评估由 `PermissionEngine`（双管线最严合并）完成，Rail 只负责调度和执行决策。
- 支持三个层次的控制：命令参数级（Pipeline A — TieredPolicy）、文件路径级（Pipeline B — FileGuardChecker）、内置安全底线（builtin_rules.yaml）。
- 支持 ASK 确认回调机制，用户可以在运行时批准或拒绝工具调用，并选择会话级记忆（持久化功能待后续多租户资源隔离适配，当前已关闭）。

## 三态决策语义

| 级别     | 语义       | Rail 行为                                                    |
| -------- | ---------- | ----------------------------------------------------------- |
| `ALLOW`  | 允许执行    | `approve()` — 正常执行工具                                     |
| `ASK`    | 需要用户确认 | `interrupt()` 或回调确认 — 中断流程，等待用户确认后恢复                |
| `DENY`   | 拒绝执行    | `reject("[PERMISSION_DENIED] ...")` — 跳过工具，返回拒绝消息给 LLM |

双管线 `strictest` 合并规则：任一管线返回 `DENY` → `DENY`；任一返回 `ASK`（且无 `DENY`）→ `ASK`；否则 → `ALLOW`。

## 核心类型

| 类型                       | 作用                                         | 何时使用                              |
| ------------------------ | -------------------------------------------- | ----------------------------------- |
| `DeepAgentConfig`        | Agent 配置，包含 `permissions` 和 `permissionHost` | 创建 Agent 时传入权限配置              |
| `PermissionEngine`       | 双管线权限引擎，持有 config 和 FileGuardChecker     | 理解权限评估逻辑时                    |
| `PermissionInterruptRail` | 工具调用拦截 Rail，执行 ALLOW/ASK/DENY 决策       | 理解 Rail 链拦截机制时                |
| `ToolPermissionHost`     | ASK 确认宿主边界，管理回调（YAML 持久化已关闭，待多租户适配） | 需要自定义确认流程时                  |
| `PermissionConfirmResponse` | 用户确认响应（approved / feedback / autoConfirm / persistAllow — persistAllow 已关闭） | 实现 ASK 回调时                    |
| `PermissionConfirmationRequest` | 确认请求载荷（toolName / toolArgs / result / autoConfirmKey） | 在回调中获取工具信息时              |
| `TieredPolicy`           | Pipeline A — 参数级规则评估                     | 理解命令 pattern 匹配时              |
| `FileGuardChecker`       | Pipeline B — 路径级规则评估                     | 理解文件路径保护时                   |
| `SeverityMapping`        | 风险级别到决策的映射（受 permission_mode 影响）    | 理解 normal/strict 区别时            |
| `BuiltinRules`           | 内置安全规则加载器（进程级缓存）                 | 理解安全底线规则时                   |
| `PermissionsYamlWriter`  | YAML 持久化写入器（原子写入） — **当前已关闭，待后续多租户资源隔离适配** | 理解 persist-allow 机制时            |

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

// 运行时持久化的放行规则（初始为空；持久化功能待后续多租户资源隔离适配，当前不启用）
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
ToolPermissionHost host = ToolPermissionHost.builder()
    .resolveWorkspaceDir(() -> workspacePath)
    // .permissionYamlPath(yamlPath)  // 待后续多租户资源隔离适配，当前已关闭持久化
    .build();

// 设置确认回调
host.setRequestPermissionConfirmationFn(req -> {
    System.out.println("确认请求: tool=" + req.getToolName()
        + " args=" + req.getToolArgs());
    // 返回确认结果
    return PermissionConfirmResponse.builder()
        .approved(true)                    // 是否批准
        .feedback("")                      // 拒绝时的反馈信息
        .autoConfirm(true)                 // 本次会话内记住（相同 key 不再询问）
        .persistAllow(false)               // 永久落盘 — 已关闭，待后续多租户资源隔离适配
        .build();
});
```

如果未设置回调，ASK 决策会走内置中断/恢复路径（`ToolInterruptException`），Agent 会暂停等待外部恢复。

### 3. 构建 DeepAgent 并注册工具

```java
DeepAgentConfig config = DeepAgentConfig.builder()
    .enableTaskLoop(true)                  // 必须开启，否则 ReAct 循环不会运行
    .enableTaskPlanning(false)
    .enableTenantIsolation(false)
    .restrictToWorkDir(false)
    .systemPrompt("你是一个助手，根据用户请求调用工具。")
    .maxIterations(8)
    .completionTimeout(120.0)
    .language("cn")
    .model(modelMap)
    .workspacePath(workspacePath.toString())
    .permissions(permissions)              // 传入权限配置
    .permissionHost(host)                  // 传入确认宿主
    .build();

AgentCard card = AgentCard.builder()
    .name("my_agent").description("工具护栏示例 Agent").build();
Workspace ws = Workspace.builder()
    .rootPath(workspacePath.toString()).language("cn").build();
DeepAgent agent = HarnessFactory.createDeepAgent(card, config, ws);

// 设置 LLM
agent.getAgent().setLlm(model);

// 注册工具
agent.registerHarnessTool(buildBashTool());
agent.registerHarnessTool(buildReadFileTool());
agent.registerHarnessTool(buildWriteFileTool());

// 初始化（此处会自动注册 PermissionInterruptRail）
agent.ensureInitialized();
```

### 4. 运行并验证

```java
Map<String, Object> inputs = new LinkedHashMap<>();
inputs.put("query", "请用 cat 命令查看主机配置");
inputs.put("conversation_id", "session_1");

Iterator<Object> stream = agent.stream(inputs);
while (stream.hasNext()) {
    Object chunk = stream.next();
    // 处理流式输出
}

agent.close();
```

## 权限配置详解

### 配置层级总览

```
permissions: Map<String, Object>
├── enabled: true/false              ← 总开关
├── schema: "tiered_policy"         ← 策略模式
├── permission_mode: normal/strict   ← 严格模式
├── tools: { bash: allow/ask/deny } ← 工具级基线
├── defaults: { "*": allow }        ← 全局默认
├── rules: [...]                    ← 参数级 pattern 规则
├── approval_overrides: [...]        ← 运行时持久化的放行（当前不启用，待多租户适配）
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
4. approval_overrides 运行时持久化的 allow → ALLOW（当前不启用，待多租户适配）
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

有 `action` 字段的规则在两种模式下行为一致。

### 默认行为

| 场景                               | 行为                           |
| -------------------------------- | ---------------------------- |
| 不传 permissions                   | 护栏不生效，所有工具直接放行               |
| 传了 permissions 但 `enabled` 缺失    | DeepAgent 不注册 Rail（护栏不生效）    |
| PermissionEngine 内部 `enabled` 缺失 | 默认 `true`（但需通过 DeepAgent 门禁） |
| 无规则匹配时 | 默认 ASK（fail-closed） |
| FileGuard 未配置时 | Pipeline B 跳过（null） |
| FileGuard 默认轴 (read/write/exec) | ASK |

### 内置安全规则

内置规则文件位于 `src/main/resources/harness/security/builtin_rules.yaml`，随 jar 打包，从 classpath 加载，**进程级单例缓存**，首次读取后不可变。

| 规则 | 风险 | 说明 |
|------|------|------|
| `shell_fs_recursive_or_forced_delete` | CRITICAL | `rm -rf /`, `find . -delete`, `shred` |
| `shell_disk_partition_or_raw_device_write` | CRITICAL | `mkfs`, `fdisk`, `dd of=/dev/` |
| `shell_download_and_execute` | CRITICAL | `curl \| bash`, `iwr \| iex` |
| `shell_obfuscated_or_dynamic_execution` | CRITICAL | `base64 -d \| bash`, `eval`, `python -c` |
| `shell_reverse_shell_or_bind_shell` | CRITICAL | `/dev/tcp/`, `nc -e`, `socat EXEC:` |
| `shell_privilege_escalation` | CRITICAL | `sudo`, `su root`, `runas`, `psexec` |
| `shell_data_exfiltration` | CRITICAL | `curl --upload-file`, `scp`, `rsync` |
| `shell_remote_execution_or_lateral_movement` | CRITICAL | `Invoke-Command`, `ssh`, `wmic` |
| `shell_fork_bomb_or_resource_abuse` | CRITICAL | `:()\{:&\};:`, `kill -9 -1` |
| `shell_system_shutdown_or_reboot` | `action: deny` | `shutdown`, `reboot`, `init 0` |

> 内置规则的设计意图是"安全底线不可绕过"——即使用户配置了 `bash: allow`，`rm -rf /` 仍然会被内置规则拦下。无法通过用户规则覆盖内置规则，只能通过修改源码 YAML 或 `enabled: false` 禁用整个护栏。

## ASK 确认

### 四态确认模型

```java
PermissionConfirmResponse.builder()
    .approved(true)       // 是否批准
    .feedback("")          // 拒绝原因（approved=false 时返回给 LLM）
    .autoConfirm(true)    // 本次会话记住（相同 autoConfirmKey 不再询问）
    .persistAllow(false)   // 永久写入 permissions YAML — 已关闭，待后续多租户资源隔离适配
    .build();
```

| approved | autoConfirm | persistAllow | 行为                                    |
| -------- | ----------- | ------------ | ------------------------------------- |
| true     | false       | false        | 允许本次调用                                |
| true     | true        | false        | 会话内 auto-confirm 相同 key 的调用           |
| true     | true        | true（已关闭）    | 待后续多租户资源隔离适配，当前等同于 persistAllow=false |
| false    | -           | -            | 拒绝，返回 feedback 给 LLM                  |

### autoConfirmKey 生成规则

- **shell 工具**：按解析后的简单子命令文本生成（如 `bash:curl`）
- **非 shell 工具**：直接用 toolName（如 `read_file`）
- **有风险结构**（管道/重定向/heredoc 等）：key 为空串，强制每次确认（fail-closed）

### 持久化写入（已关闭）

> **待后续多租户资源隔离适配**。当前版本已关闭 `persistAllow` 持久化路径，相关配置开关（`permissionYamlPath`、`persistAllow(true)`）均不启用。后续在多租户资源隔离方案落地后将重新开放。

原有持久化流程设计为：
1. `PermissionsYamlWriter.mergeAllowRule()` 合并 allow 规则到当前配置
2. `host.persistAllowRule(merged)` 写入 YAML 文件（临时文件 + 原子移动）
3. `engine.refreshConfig(merged)` 原子替换内存配置（volatile + synchronized）

## 运行时修改

### 会话级自动确认

通过 ASK 确认回调返回 `approved=true, autoConfirm=true, persistAllow=false`，存入 `ConcurrentHashMap`，Agent 关闭后失效。

### 手动代码调用

如果持有 `PermissionEngine` 引用，可以直接调用：

```java
engine.refreshConfig(newPermissionsMap);  // 原子替换内存配置
```

`refreshConfig` 会重建 `FileGuardChecker`（重新编译 file_guard 规则），原子替换 volatile 引用。

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
| `glob` | Glob 通配符匹配 | path=`**/.env*` 匹配 `/work/.env.local` |

> **大小写敏感性**：Windows 上 `prefix` 与 `glob` 匹配均为大小写不敏感（详见[跨平台注意事项](#大小写不敏感匹配windows)）；Linux 上区分大小写。例如 Windows 上 deny 规则 `D:/tmp/*.txt` 同样拦截 `d:/tmp/cookies.txt`、`D:/TMP/COOKIES.TXT` 等大小写变体。

### 轴蕴含规则

- `WRITE` 操作同时检查 `READ` 轴（write ⇒ read 蕴含）
- `EXEC` 操作同时检查 `READ` 轴（exec ⇒ read 蕴含）
- 取 `strictest(action_level, read_level)` 作为最终决策

## 跨平台注意事项

### 大小写不敏感匹配（Windows）

**Windows 平台上所有模式匹配统一为大小写不敏感**，包括：

| 匹配场景 | 实现类 | 说明 |
|---------|--------|------|
| Pipeline A 参数级规则（wildcard / regex） | `RuleMatcher`、`WildcardMatcher` | 命令与参数匹配不区分大小写 |
| Pipeline B `file_guard` glob 匹配 | `GlobMatcher` | glob 规则与实际路径不区分大小写 |
| Pipeline B `file_guard` prefix 匹配 | `FileGuardChecker.matchesPrefix` | 前缀比较前统一转小写 |

**设计理由**：Windows 文件系统（NTFS）不区分大小写，`D:/tmp/cookies.txt` 和 `d:/TMP/COOKIES.TXT` 指向**同一物理文件**。若匹配区分大小写，攻击者（或 LLM 生成的路径）只需变换大小写即可绕过 deny 规则读取受保护文件。因此与文件系统语义对齐，Windows 上统一不敏感。

**Linux 平台保持大小写敏感**（ext4/xfs 等文件系统区分大小写，`Readme.md` 与 `README.md` 是不同文件），行为与 Python 原版一致。

**示例**：Windows 上 deny 规则 `D:/tmp/*.txt`（glob）可拦截以下全部变体：

```
D:/tmp/cookies.txt          ← 精确匹配
d:/tmp/cookies.txt          ← 小写盘符
D:/TMP/cookies.txt          ← 大写目录
D:/tmp/COOKIES.TXT          ← 大写文件名+扩展名
d:/Tmp/Cookies.txt          ← 混合大小写
D:\tmp\cookies.txt          ← 反斜杠（posix 化后匹配）
D:/tmp/./cookies.txt        ← 点号段（normalize 后匹配）
D:/tmp/sub/../cookies.txt   ← 遍历段（normalize 后匹配）
```


### Windows 路径问题

在 Windows 上，`/etc/hosts` 不是绝对路径（无盘符），`PathAccessExtractor.resolvePathStr()` 会将其解析为 `workspace.resolve("/etc/hosts")` → `tempDir/etc/hosts`，这与 file_guard 规则中的 `/etc/hosts` 不匹配，导致回退到默认值。

**解决方案**：使用 workspace 相对路径（如 `tempDir.resolve("protected_hosts")`），在配置和工具调用中使用同一路径。

### enableTaskLoop 必须开启

`DeepAgentConfig.enableTaskLoop(true)` 是 ReAct 循环运行的前提。如果设为 `false`，`DeepAgent.invokeInternal()` 会短路返回元数据，根本不调用 `ReActAgent.invoke()`，工具护栏不会被触发。

## 完整示例

仓库内提供了一个完整的可运行示例：

- **示例文件**：`examples/deep_agent/DeepAgentToolGuardrailExample.java`
- **特点**：使用确定性 Fake LLM，无需 API Key 或网络依赖
- **覆盖场景**：
  1. ALLOW — `bash=allow` + curl/rm deny 规则，cat 命令执行
  2. DENY — `curl *` deny 规则拦截
  3. ASK approved — 回调确认后工具执行
  4. ASK rejected — 回调拒绝后工具被跳过
  5. file_guard read — `read=allow` 路径规则，read_file 执行
  6. file_guard write — `write=deny` 路径规则，write_file 被拦截

### 运行方式

```bash
cd agent-core-java
mvn -DskipTests compile
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -DincludeScope=test -q
javac -encoding UTF-8 -source 17 -target 17 -cp "target/classes;target/dependency/*" \
  -d examples/deep_agent/build examples/deep_agent/DeepAgentToolGuardrailExample.java
java -Dfile.encoding=UTF-8 -cp "examples/deep_agent/build;target/classes;target/dependency/*" \
  examples.deep_agent.DeepAgentToolGuardrailExample
```

## 当前实现边界

- 工具护栏拦截所有工具调用（`intercept=all_tools`），不限于 `permissions.tools` 中列出的工具。
- Shell 工具名归一化：`mcp_exec_command`、`create_terminal` 统一为 `bash`。
- Shell AST 当前使用保守扫描器（纯字符串扫描），后续将接入 tree-sitter SPI 后端。
- 内置规则不可通过用户规则覆盖，只能通过修改源码 YAML 或 `enabled: false` 禁用整个护栏。
- YAML 持久化功能已关闭，待后续多租户资源隔离适配后重新开放。

## 参考入口

- 设计方案文档：`../../../output/tool_guardrail_design.md`
- 示例代码：`../../../../examples/deep_agent/DeepAgentToolGuardrailExample.java`
- E2E 测试：`../../../../jiuwen-test/src/test/java/com/openjiuwen/test/cases/harness/security/ToolGuardrailE2E001.java`
- 引擎源码：`../../../../src/main/java/com/openjiuwen/harness/security/PermissionEngine.java`
- Rail 源码：`../../../../src/main/java/com/openjiuwen/harness/rails/security/PermissionInterruptRail.java`
- 内置规则：`../../../../src/main/resources/harness/security/builtin_rules.yaml`
- 内容风险护栏（不同子系统）：`安全护栏Guardrail.md`
