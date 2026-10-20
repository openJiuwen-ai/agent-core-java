# Context 卸载与装载（Offload / Reload）

Context Offload 是上下文引擎的「宽度控制」能力：当单条消息过大（典型场景是工具返回大体积结果）时，把原文外置到存储后端，上下文中只保留短预览 + `[[OFFLOAD: ...]]` 标记；模型需要原文时，通过内置工具 `reload_original_context_messages` 按句柄取回。全链路零 LLM 调用，结果确定、可复现。

它与压缩（Compress）正交协作：

| 机制 | 定位 | 目标对象 | 可逆性 | 是否调 LLM |
| --- | --- | --- | --- | --- |
| Offload（本文） | 宽度控制：大消息外置 + 按需取回 | 超大单条消息（默认 tool 角色） | 原文可恢复（Reload） | 否 |
| Compress（见[上下文引擎](上下文引擎.md)） | 深度控制：多轮对话历史摘要 | 整段对话叙事 | 不可逆（摘要替换） | 是 |

设计背景与完整方案见 `output/Context卸载加载补齐方案-v2.md`（GitCode Issue #169）。

## 功能开关

两个开关都由 `ContextEngineConfig` 承载：

| 配置项 | JSON 属性 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enableOffload` | `enable_offload` | `false` | 卸载总开关。默认关闭；开启后框架在 `initContext` 时自动接线 v2 管道（存储链、Catalog、Janitor、保护配置同步） |
| `enableReload` | `enable_reload` | `false` | 装载工具开关。开启后自动注册内置工具 `reload_original_context_messages`，并在系统消息追加取回提示词 |

> 注意：`enable_reload` 只控制「模型能不能取回」，`enable_offload` 才控制「要不要卸载」。只开 reload 不开 offload 时，上下文中不会出现 marker；只开 offload 不开 reload 时，卸载照常发生但模型无法主动取回。

## 触发条件

以代码实际实现为准（`MessageOffloader`），触发是两道门 + 一组过滤：

1. **总量门控（`tokens_threshold`）**：每次 `addMessages()` 时，统计「现有上下文 + 新增消息」的总 token 数，`>= tokens_threshold`（默认 20000，含边界）且存在可卸载候选时才执行卸载扫描。未达阈值时不做任何替换。
2. **单条边界（`large_message_threshold`）**：扫描时逐条判定，消息内容**字符数** `>= large_message_threshold`（默认 1000，含边界）才卸载。
3. **过滤条件**（任一命中则跳过该条）：
   - 消息 role 不在 `offload_message_type`（默认 `["tool"]`）内；
   - 消息 metadata 带 `openjiuwen.offload_done=true`（防重：已卸载过的、或 reload 结果，不再二次卸载）；
   - 消息位于保护窗口内（最近 `recent_message_protection_count` 条，默认 5；窗口随新消息滑动前移，最早受保护的消息恢复可卸载）;
   - 消息来自受保护工具（`protected_tool_names` 命中工具名）；
   - 消息总数 `<= messages_to_keep`（可选，默认不启用）；
   - `keep_last_round=true`（默认）时，最后一轮 assistant 消息及之后的内容不卸载。

> 口径说明：设计文档 v2 §7.2.1 表述为「单条消息 token 阈值」，实际代码实现是「总量 token 门控 + 单条字符边界」两层（`tokens_threshold` 管总量、`large_message_threshold` 管单条字符数）。文档以代码为准。

## 卸载后的影响

一条 tool 消息被卸载后：

- **上下文中**：原内容替换为「预览 + marker」。预览 = 原文前 `trim_size`（默认 100）个字符 + `"..."`；marker 形如：

  ```text
  [[OFFLOAD: handle=a1b2c3d4e5f6789012345678abcdef01, type=filesystem, path=/data/offload/context/_/_/s1_context/offload/a1b2....json]]
  ```

  `type` 取值：`filesystem` / `in_memory` / `redis`（与实际落盘后端一致，降级后会变化）；`path` 为文件路径（FILESYSTEM）或存储 key（REDIS/MEMORY），可能省略。
- **消息对象上**：替换为 `OffloadToolMessage` 等对应角色类型，携带结构化字段 `offload_handle` / `offload_type`。
- **存储中**：完整原文以 JSON payload 落到所配后端。
- **失败时**：所有后端写失败 → 整体不生效，原文保留 inline（keep inline），只记一条 warn 日志，不影响本次工具调用的成功语义。
- **reload 非消费式**：取回成功后，存储中的原文**保留**，可以多次取回。

## 如何配置

### 最小可用配置（ReActAgent）

```java
OffloadConfig offloadConfig = OffloadConfig.builder()
        .primaryBackend(OffloadBackend.FILESYSTEM)   // 默认值，可省略
        .workspaceRoot("/data/offload")              // FILE 后端根目录
        .build();

ContextEngineConfig engineConfig = ContextEngineConfig.builder()
        .maxContextMessageNum(200)
        .defaultWindowRoundNum(10)
        .enableReload(true)      // 注册 reload_original_context_messages 工具
        .enableOffload(true)     // 打开卸载管道
        .offload(offloadConfig)
        .build();

// 处理器链注入 MessageOffloader
List<ContextEngine.ProcessorSpec> processors = List.of(
        new ContextEngine.ProcessorSpec("MessageOffloader", new MessageOffloaderConfig()));
```

`enable_offload=true` 时框架（`ReActAgent.bindOffloadPipeline`）自动完成：按 `primary → fallback → final_fallback` 组装存储链、绑定 per-context Catalog、启动 Janitor 清理线程、把 `OffloadConfig` 中的保护配置同步到 offloader。管道绑定失败会降级为 legacy 模式，不阻断 agent 主流程。

### DeepAgent 用户

`DeepAgentConfig` 不携带 Context Offload 配置，需要在 `ensureInitialized()` 之前拿到内部 `ReActAgent` 原地注入（完整可运行示例见 `examples/deep_agent/.../ContextOffloadExample.java`）：

```java
ReActAgent reactAgent = (ReActAgent) deepAgent.reactAgent();
ReActAgentConfig agentConfig = (ReActAgentConfig) reactAgent.getConfig();
agentConfig.setContextEngineConfig(engineConfig);
agentConfig.setContextProcessors(List.of(
        new ContextEngine.ProcessorSpec("MessageOffloader", offloaderConfig)));
```

### 存储后端与降级链

| 后端 | 枚举值 | 适用 | 语义 |
| --- | --- | --- | --- |
| 文件系统 | `FILESYSTEM` | 默认生产 | 原子写盘（tmp → fsync → rename），跨 turn 可恢复 |
| Redis | `REDIS` | 多实例、跨进程共享 | `SET key value EX ttl`，TTL 原生 |
| 内存 | `MEMORY` | 单机兜底 | 进程内 ConcurrentHashMap，进程退出即失 |
| 组合 | `COMPOSITE` | 框架内部 | 按链降级的聚合，不是可配置的 commit 目标 |

降级链默认 `FILESYSTEM → REDIS → MEMORY`：主后端写失败自动切下一个，全部失败 keep inline。两点约束：

- 链中配了 `REDIS` 但未配置 `redis` 连接块时，该环节自动跳过（链退化为 `FILE → MEMORY`）；
- Redis 使用**独立连接池**（`OffloadRedisConfig`），不与全局 Redis/Checkpointer 共享池（可以指向同一实例）。

## 配置参考

### OffloadConfig（`ContextEngineConfig.offload` 块）

| JSON 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `primary_backend` | `FILESYSTEM` | 主后端 |
| `fallback_backend` | `REDIS` | 备后端（未配 `redis` 时跳过） |
| `final_fallback` | `MEMORY` | 兜底后端 |
| `workspace_root` | `{user.dir}/offload` | FILESYSTEM 后端根目录 |
| `ttl_seconds` | `86400`（24h） | 条目存活时长；Redis TTL 与之一致 |
| `janitor_interval_seconds` | `1800`（30min） | 清理扫描周期 |
| `max_bytes_per_session` | `536870912`（512MB） | session 级字节配额，超限 LRU 驱逐未 pin 条目 |
| `max_entries_per_session` | `2000` | session 级条目配额 |
| `recent_message_protection_count` | `5` | 最近 N 条保护窗口（0 关闭） |
| `protected_tool_names` | `["reload_original_context_messages"]` | 受保护工具名集合 |
| `redis` | `null` | `OffloadRedisConfig` 块，配置后才启用 REDIS 环节 |
| `enable_otel` | `false` | 是否额外输出 OTel 指标 |
| `orphan_ttl_seconds` | `3600` | 孤儿条目 TTL 缩短值（秒），`0` 禁用 |
| `enable_file_lock` | `false` | 共享文件系统多实例部署时开启 sidecar 文件锁 |

`OffloadRedisConfig`（`offload.redis` 块）：`host`（默认 localhost）、`port`（默认 6379）、`password`、`database`（默认 0）、`connect_timeout_ms`（2000）、`so_timeout_ms`（2000）、`max_total`（8）、`max_idle`（8）。

### MessageOffloaderConfig（ProcessorSpec 配置）

| JSON 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `tokens_threshold` | `20000` | 总量门控：上下文总 token 达到即允许卸载扫描 |
| `large_message_threshold` | `1000` | 单条边界：消息字符数达到才卸载 |
| `trim_size` | `100` | 预览保留字符数 |
| `offload_message_type` | `["tool"]` | 允许卸载的消息角色（user / assistant / tool） |
| `protected_tool_names` | `["reload_original_context_messages"]` | 受保护工具名 |
| `recent_message_protection_count` | `5` | 最近 N 条保护窗口 |
| `messages_to_keep` | `null` | 消息总数不超过该值时不触发 |
| `keep_last_round` | `true` | 最后一轮不卸载 |
| `messages_threshold` | — | **已废弃**：v2 移除了消息数触发，仅兼容解析，不再生效 |

`ToolResultBudgetProcessor`（`MessageOffloader` 子类，按轮次预算卸载）另有一套默认值：`tokens_threshold=50000`、`large_message_threshold=10000`、`trim_size=3000`，并支持 `tool_name_allowlist` 白名单。

> 配置优先级：`ReActAgent` 接线时会把 `OffloadConfig` 的 `recent_message_protection_count` / `protected_tool_names` 同步覆盖到 offloader（仅当显式配置了非默认值），因此这两个参数建议在 `OffloadConfig` 中统一配置。

## 数据结构与存储布局

### 输出路径（FILESYSTEM）

```text
{workspace_root}/context/{tenant}/{session}_context/offload/{handle}.json
```

- `handle`：32 位 UUID hex，namespace 内唯一，不复用；
- namespace 为三级结构 **tenant / session / context**（与平台多租户模型对齐，无独立 user 层）；namespace 段（tenant/session）写入前会 sanitize：仅保留 `[A-Za-z0-9._-]`，其余字符替换为 `_`，空段与 `.`/`..` 变为 `_`（防路径穿越）；
- 默认装配（`ReActAgent`）下 tenant 为空串，实际路径形如 `{workspace_root}/context/_/{sessionId}_context/offload/{handle}.json`；
- 典型场景会同时出现 `{handle}.json.tmp.<uuid>` 中间文件（写完即原子 rename，失败自动清理）与 `{handle}.json.lock`（仅 `enable_file_lock=true` 时，发布后删除）。

### Redis Key

```text
offload:{tenant}:{session}:{context}:{handle}
```

例如：`offload:_:offload_demo_1729400000000:default:a1b2c3d4e5f6789012345678abcdef01`。段同样经过 sanitize；写入即 `SETEX`，TTL 与 `ttl_seconds` 一致，到期 Redis 自动删除。

### Payload JSON（schema_version=2）

```json
{
  "schema_version": 2,
  "offload_handle": "a1b2c3d4e5f6789012345678abcdef01",
  "namespace": {"tenant_id": "_", "session_id": "s1", "context_id": "c1"},
  "created_at": 1760900000000,
  "expires_at": 1760986400000,
  "content_sha256": "<messages 紧凑 JSON 的 sha256>",
  "messages": [ { "...": "BaseMessage.modelDump() 形态" } ]
}
```

`content_sha256` 用于装载时完整性校验：不匹配或 JSON 解析失败会被标记 CORRUPT 并拒绝返回，不会把可能损坏的内容交给模型。

## 装载（Reload）使用说明

`enable_reload=true` 时，框架为每个上下文自动注册工具 `reload_original_context_messages`，同时在系统消息追加取回提示词（模型看到 `[[OFFLOAD: ...]]` 标记时会自行决定是否调用）。

**参数**：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `offload_handle` | 是 | marker 中的 handle，原样传入 |
| `offload_type` | 是 | marker 中的 type（`filesystem` / `in_memory` / `redis`），原样传入 |
| `offset` | 否 | 0-based 起始消息序号（分页用，默认 0） |
| `limit` | 否 | 最多返回的消息条数（0 = 不限） |

**成功输出**：`reload messages with handle=<id> (offset=N):` 后跟逐条 JSON dump 的原始消息，并附带 `openjiuwen.offload_done=true`（该标记防止 reload 结果再次进入卸载链形成循环）。

**失败输出**：结构化错误码文本，不抛异常、不中断轮次，不暴露堆栈与服务器内部路径：

| 错误码 | 含义 |
| --- | --- |
| `OFFLOAD_MISSING` | handle 在 Catalog 中不存在（含未发布完、跨调用新建 context 的场景，见下文生命周期） |
| `OFFLOAD_EXPIRED` | 条目已过 TTL（Redis 到期即不可读） |
| `OFFLOAD_CORRUPT` | sha256 校验失败或 JSON 损坏 |
| `OFFLOAD_GONE` | Catalog 有记录但存储副本已丢失 |
| `OFFLOAD_FORBIDDEN` | namespace 归属校验失败（跨 session 伪造 handle） |

**安全约束**：namespace 一律取自服务端 session 上下文，不信任模型传入的 handle/type 做归属判断；仅凭 handle 无法跨 session 取回内容。

## 生命周期与清理

- **Catalog 是 per-context 的内存对象**：每次 `Runner` 调用新建 context 时重新绑定。上一轮调用的 handle 在新一轮的 Catalog 中查不到，reload 会返回 `OFFLOAD_MISSING`（落盘文件仍在，由 Janitor 按孤儿策略回收）。同一 context 内（含多轮 ReAct 循环）handle 始终有效。
- **TTL 清理**：Janitor 按 `janitor_interval_seconds` 周期扫描，删除过期且未 pin 的条目并标记 EXPIRED；Redis 依赖原生 TTL 到期。
- **配额清理**：session 字节/条目超配额时按 LRU 驱逐最旧未 pin 条目。
- **孤儿回收**：Janitor 扫描两类孤儿——滞留 PENDING 状态的条目、无 Catalog 记录且过期的落盘文件（目录遍历深度 5），按 `orphan_ttl_seconds`（默认 1h）加速回收；`0` 禁用。
- **Pin 保护**：压缩期间，待压缩范围内的条目会被临时 pin（约 5 分钟），防止压缩（LLM 调用耗时 30-60s）过程中被 Janitor 清掉；reload 来源消息被压缩时自动 `extendTtl` 延长 TTL。
- **session 清理**：`engine.clearContext(...)` / `saveContexts` 生命周期动作会联动清理对应 namespace 下的卸载条目。

## 与压缩的交互

压缩（Compress）处理含 marker 的消息时，框架通过 `OffloadMarkerUtils` 保证可取回性：

- 压缩 prompt 强制要求保留 `[[OFFLOAD: ...]]` 原文，压缩完成后做后处理校验，发现丢失的 handle 会以结构化清单（`[Offloaded Content Summary]`）追加回摘要；
- 压缩替换消息时把 `offload_handle` / `offload_type` 注入摘要消息 metadata；
- 压缩期间 pin + TTL 延长，避免「压缩还没完，原文先被清了」。

## 可观测性

每次 save/load/delete 记录结构化指标 `OffloadMetric`：

- **Slf4j（默认开）**：结构化日志含 `algorithmId`（触发 processor）、`handle`、`success`、`failureReason`、`preTokenCount` / `postTokenCount`（卸载前后 token）、`durationMs`、`backend`。失败归因枚举：`ENOSPC` / `IO_ERROR` / `REDIS_DOWN` / `SHA_MISMATCH` / `JSON_CORRUPT` / `NAMESPACE_MISMATCH` / `STATUS_INVALID`。
- **OTel（`enable_otel=true`）**：meter `openjiuwen.context.offload`，三个 Counter——`offload.operations`（attributes：event / algorithm / backend / success / failureReason）、`offload.token.delta`、`offload.duration`。OTel 调用全程防御式包裹，观测故障不会影响卸载主链路。

## 约束与边界速查

| 场景 | 行为 |
| --- | --- |
| 未超阈值 | 原文 inline，不做任何替换 |
| token 恰好等于阈值 / 字符数恰好等于单条阈值 | 触发（含边界） |
| 所有后端写失败 | keep inline，原文保留，仅告警 |
| 写盘中进程崩溃 | tmp 文件残留但不影响读取（未 rename 不可见），重启后 reload 返回 `OFFLOAD_MISSING` |
| 保护窗口 ≥ 消息总数 | 卸载范围为空，不卸载任何消息 |
| reload 结果过大 | 不会二次卸载（`offload_done` 防重），可用 `offset`/`limit` 分页取回 |
| 并发同 session 卸载 | SessionOffloadGate 条纹锁串行化 |
| 多实例共享文件系统 | `enable_file_lock=true` 开启发布锁；跨进程并发写同一 handle 场景才需要 |
| 管道绑定失败 | 降级 legacy 模式，agent 主流程不受影响 |

## 示例与测试入口

- **端到端示例**：`examples/deep_agent/src/main/java/examples/deep_agent/ContextOffloadExample.java`（运行方式与环境变量见 `examples/deep_agent/README.md` 的 `offload` 一节：需要真实 LLM，Redis 可选）
- **UT**：`core/src/test/java/com/openjiuwen/core/context/offload/`（Store 三后端、降级链、Catalog 状态机、孤儿扫描等）
- **管道测试**：`core/src/test/java/com/openjiuwen/core/context/processor/offloader/MessageOffloaderOffloadV2Test.java`、`.../context/OffloadMessageBufferTest.java`

## 参考入口

- [API 文档：MessageOffloader](../API文档/com.openjiuwen.core/context/processor/offloader/MessageOffloader.md)
- [API 文档：MessageOffloaderConfig](../API文档/com.openjiuwen.core/context/processor/offloader/MessageOffloaderConfig.md)
- [API 文档：SessionModelContext](../API文档/com.openjiuwen.core/context/context/SessionModelContext.md)
- [API 文档：ContextEngineConfig](../API文档/com.openjiuwen.core/context/schema/ContextEngineConfig.md)
- [上下文引擎](上下文引擎.md)（上下文主线与 Compress 机制）
- 设计文档：`output/Context卸载加载补齐方案-v2.md`
