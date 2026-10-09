# Ascend 算力亲和（KV Cache Affinity）

这里重点说明 Java 版的 Ascend-affinity 算力亲和调度：多轮会话的 KV cache 必须落在同一个 Ascend 设备上才有命中价值。主线是请求体顶层携带 `agent_hint`（`session_id` / `parent_session_id`），由带 affinity 插件的 Ascend 网关按会话亲和性把请求调度到同一设备复用缓存；Java 侧则负责在正确的时机触发 `prefetch`（预取）、`offload`（卸载到外部存储）、`evict`（驱逐）三种管理动作，并保证与正在进行的推理互不干扰。

## 功能定位

先看四个关键问题：

1. 怎样打开 Ascend-affinity：模型客户端要声明什么能力，Agent 要打开哪个开关。
2. `prefetch` / `offload` / `evict` 三种管理动作分别在什么时候被触发，由谁触发。
3. 多级会话（子代理、压缩器、团队成员）的缓存身份怎样归属与隔离，哪些子代理跨调用保留缓存。
4. 当前实现的边界与已知缺口在哪里。

## 核心概念

| 对象 | 作用 | 你需要关心什么 |
| --- | --- | --- |
| `KVCacheRuntime` | 调度核心（`KVCacheTypes.KVCacheRuntimeProtocol` 实现） | 进程级单例，由应用引导创建并注入会话；动作队列、准入控制、状态机都在这里 |
| `KVCacheIdentity` | 缓存树身份 `(cacheId, parentCacheId)` | 网关把缓存组织成树，驱逐 root 会级联清空子树 |
| `KVCacheControlDomain` | 控制域四元组 | `(provider, apiBase, modelName, cacheNamespace)`——同一 session 在不同端点/模型/命名空间下是不同缓存对象 |
| `AgentHint` | 线上协议构造器 | 身份 hint 与 `context_management.edits`；校验失败即关闭，非法 hint 不会到达网关 |
| `Model.KvCacheAffinityClient` | 客户端能力接口 | `OpenAIModelClient`（含 `OpenAiCompatibleModelClient`）实现；`supportsKvCacheAffinity()` + 三个动作方法 |
| `KVCacheModelCallHook` | 模型调用挂钩 | 每次模型调用前做窗口差异驱逐、注入 hint、取推理租约 |
| `KVCacheSubagentLifecycle` | 子代理缓存生命周期 | sticky 判定、子会话创建、进入/结束的缓存动作 |
| `KVCacheConfig` | 动作超时预算 | 各动作的整体预算（含 HTTP 与重试），默认 2s～5s |

### 两级状态机

`KVCacheRuntime` 内部维护两套状态：

- **binding 级 residency**（缓存在哪一侧）：`UNKNOWN → RESIDENT`（预取或推理成功）`→ OFFLOADED`（卸载成功）`→` release/close 移除。
- **scope 级 admission**（推理可否直接进入）：`OPEN → BLOCKED`（suspend 置位，此时推理先自动预取）`→ OPEN`（预取完成或 fail-open 强制放行）`→ TERMINAL`（release 后永久关闭）。

两个保证：同一 key 的管理动作串行化并有依赖链；**推理永远不会被缓存管理阻塞**——准入发现 BLOCKED 会先自动预取，重查仍阻塞则 fail-open 放行。

### 线上协议 agent_hint

- **身份 hint**：普通推理请求体顶层 `{session_id, parent_session_id}`，网关据此做设备亲和。
- **管理 hint**：管理动作请求附加 `context_management = {manage_request: true, edits: [...]}`；edits 按 `target`（session / messages / tools）生成，区间为半开 `[start, end)`。
- 亲和信息只走 JSON body（`extra_body` 摊平为顶层字段），不新增 HTTP header。

### 三种动作的触发时机

| 动作 | 触发时机 | 触发方 |
| --- | --- | --- |
| `prefetch` | sticky 子代理进入时（`prepareKvc`）；推理准入发现 scope BLOCKED 时自动预取 | `KVCacheSubagentLifecycle.prepareSubagent`、`KVCacheRuntime.tryAcquireOnce` |
| `offload` | `session.suspendKvc()`——典型场景是 sticky 子代理成功结束后保留缓存 | `KVCacheSubagentLifecycle.finishSubagent`、业务侧显式调用 |
| `evict` | 模型调用前窗口差异检测发现历史被修改（上下文压缩等）；会话终态 `releaseKvc()`；非 sticky 子代理结束；`runtime.close()` 收尾 | `KVCacheModelCallHook.handleContextWindowChange`、`AgentSession.releaseKvc` |

心智模型：

- **推理链路**只带身份 hint，网关负责设备亲和与复用。
- **管理链路**在正确时机下发 edits，Java 侧不阻塞推理（fail-open：缓存管理失败只记日志，照常推理）。

### 子代理与稳定会话 id

TaskTool 派生子代理时，子代理通过 `KVCacheChildSession` / `KVCacheSubagentLifecycle` 共享父会话的 runtime，并把缓存身份挂到父缓存根下。哪些子代理类型跨调用复用同一子会话 id（即同一缓存身份）由父配置的 `stableSubSessionTypes` 决定；同时 **sticky 判定也跟随这个列表**：

- 列表内类型：进入时 prefetch，成功后 suspend（offload 保留缓存）。
- 列表外类型：不预取，结束后直接 evict。

默认列表为 `["browser_agent", "verification_agent"]`。

### 缓存身份示例

各会话角色实际生成的缓存身份（可通过 demo 网关记录的 `agent_hint` 直接观察）：

| 会话角色 | cacheId 示例 | parentCacheId 示例 | 说明 |
| --- | --- | --- | --- |
| 主会话（自指根） | `kvc-demo-session` | `kvc-demo-session` | 会话是自己的缓存根 |
| sticky 子代理 | `<parent>_sub_verification_agent` | `<parent>` | 稳定 id，跨调用复用同一缓存 |
| 非 sticky 子代理 | `<parent>_sub_code_819261de` | `<parent>` | UUID 后缀导致跨调用缓存身份不同 |
| 压缩器子缓存 | `<session>:compressor:round-level` | `<session>` | 压缩推理与主会话缓存亲和隔离 |
| 团队成员 | `team:<session>:team:<teamId>:member:<agentId>` | `<session>` | 成员缓存挂在 team 会话根下 |

### 端到端时序（四条链路）

```
业务代码            会话/运行时                  模型调用挂钩/客户端        Ascend 网关
   │                     │                           │                     │
   │ 首轮推理     │ 绑定登记 + 血缘解析       │                     │
   ├────────────────────▶│──────────────────────────▶│ 身份 agent_hint     │
   │                     │                           ├────────────────────▶│ 设备亲和命中
   │                     │                           │                     │
   │ 窗口被改写   │                           │                     │
   │───────下一轮推理───────────────────────────────▶│ 差异检测：evict     │
   │                     │                           ├────────────────────▶│ messages[1,2)
   │                     │                           │                     │
   │ releaseKvc() │ 入队 EVICT + 解绑         │                     │
   ├────────────────────▶│──────────────────────────▶│──evict(session)────▶│ 缓存树级联清理
   │                     │                           │                     │
   │ 子代理派生   │ TaskTool sticky 判定      │                     │
   │                     │ 进入 prefetch             ├──prefetch(sub)─────▶│
   │                     │ 成功后 offload            ├──offload(sub)──────▶│
   │                     │ 非 sticky 结束 evict      ├──evict(sub)────────▶│
```

## 快速开始

三步打开 affinity：配置 affinity 客户端、注入 runtime、打开 Agent 开关。

```java
// 1. 模型客户端声明 affinity 能力
ModelClientConfig clientConfig = ModelClientConfig.builder()
        .clientProvider("AscendAffinity")            // 默认已注册，别名 ascend_affinity
        .apiKey("sk-xxx")
        .apiBase("http://ascend-gateway:8000")       // 带 affinity 插件的 vLLM 兼容端点
        .verifySsl(false)
        .extraFields(Map.of("extensions",
                Map.of("kv_cache", Map.of("mode", "affinity"))))   // 能力声明就靠它
        .build();
Model model = new Model(clientConfig,
        ModelRequestConfig.builder().modelName("GLM-5.2").build());

// 2. 创建进程级 runtime 并注入会话（bindingProvider 兜底冷会话）
KVCacheRuntime runtime = new KVCacheRuntime(new KVCacheConfig(), () -> model);
AgentSession session = new AgentSession(sessionId, envs, card,
        null, false, Map.of(), runtime);

// 3. Agent 打开开关并运行
ReActAgentConfig config = ReActAgentConfig.builder()
        .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
        .build();
config.setEnableKvCacheAffinity(true);
agent.configure(config);
agent.setLlm(model);
Runner.runAgent(agent, Map.of("query", "..."), session, null);  
```

之后的模型调用会自动携带身份 hint；窗口差异驱逐、准入自动预取、推理租约收尾全部由挂钩完成，业务代码无需感知。

## 配置总览

| 配置点 | 字段 / 键 | 默认 | 说明 |
| --- | --- | --- | --- |
| `ModelClientConfig` | `extensions.kv_cache.mode` | 无 | 置 `affinity` 声明能力；不配置则 `supportsKvCacheAffinity()` 为 false |
| `ModelClientConfig` | `extensions.kv_cache.affinity_field` | `agent_hint` | 请求体顶层 hint 字段名 |
| `DeepAgentConfig` | `enableKvCacheAffinity` | `false` | Agent 级开关，与模型能力同时满足才生效 |
| `DeepAgentConfig` | `stableSubSessionTypes` | `["browser_agent", "verification_agent"]` | 跨调用复用稳定子会话 id 的子代理类型，同时决定 sticky 判定 |
| `DeepAgentConfig` | `enableTaskLoop` | `false` | DeepAgent 真正执行推理需要打开（`invokeInternal` 的任务循环入口） |
| `KVCacheConfig` | `actionTimeout` / `evictTimeout` / `closeTimeout` | 2s / 3s / 5s | 动作整体预算；离线 demo 建议 `KVCacheConfig(5.0, 5.0, 5.0)` |

## 子代理 sticky 生命周期

TaskTool 派生子代理时的完整时序：

1. `buildSubSessionId` 生成子会话 id：`stableSubSessionTypes` 内的类型复用 `parent_sub_<type>`（跨调用稳定），其它类型追加 UUID 后缀。
2. `KVCacheChildSession.buildChildSessionKwargs` 复制父会话 env 并写入父缓存 id，`createSubagentSession` 创建共享 runtime 的子会话并 `bindParentSessionId`。
3. 进入：sticky 类型 `prepareKvc()` → 网关收到该子缓存身份的 `prefetch`。
4. 执行：子代理推理携带子缓存身份的 `agent_hint`，与主会话缓存隔离。
5. 结束：sticky 类型成功后 `suspendKvc()`（offload 保留）；非 sticky 类型或失败一律 `releaseKvc()`（evict 驱逐）。

## 示例：kv-cache-demo 的实现原理

示例工程 `examples/kv-cache-demo` 是独立 Maven 工程，依赖本地仓库的 `agent-core-java`，内嵌一个 OpenAI 兼容的 mock 网关，因此整条调度链路**离线可跑、可断言**。两个入口：

- `AscendAffinitySchedulingDemo`（默认主类）：五条链路的调度语义验证。
- `AscendAffinityKvCacheDemo`：三个管理动作的协议级冒烟。

### mock 网关（AffinityMockGateway）

`com.sun.net.httpserver.HttpServer` 实现的 OpenAI 兼容端点，只做两件事：

1. 对每个 `POST /v1/chat/completions` 请求，从请求体提取 `agent_hint`（或 `extra_body.agent_hint`）**按到达顺序记录**；
2. 返回一个固定的最小合法 completion JSON（200）。

它不是简单的打桩：**记录下来的 `agent_hint` 序列就是整条调度链路的可观测输出**——推理身份 hint、evict/offload/prefetch 的 edits 与先后次序，全部来自真实链路（runtime 状态机 → 模型调用挂钩 → 客户端协议）的实际行为，而不是对返回值的猜测。

### 断言原理：按序核对 hint 序列

demo 的每条链路先 `gateway.reset()` 清空记录，执行触发动作后按序核对 hint 序列（用 Jackson 解析后断言 `session_id` / `parent_session_id` / `edits` 的类型、目标与区间）。这比断言方法返回值严格得多：例如 chain2 断言的不是"evict 返回 true"，而是"下一次推理之前，网关必须先收到 `messages` 区间的 evict 编辑"——触发时机错了会直接 FAIL。

异步动作（suspend/release 只保证入队）通过轮询等待 edit 到达（上限 15s）再继续，保证时序断言确定性。

### 五条链路的编排要点

| 链路                | 触发机制 | 编排关键点 |
|-------------------| --- | --- |     
| chain1 推理建立亲和     | `Runner.runAgent` 首轮模型调用 | 内层挂钩完成血缘解析、绑定登记、身份 hint 注入 |
| chain2 窗口差异 evict | demo 内置 `WindowRewriteProcessor` 在第二轮**改写**（而非追加）会话窗口，模拟上下文压缩 | 追加式窗口视为无变化不驱逐；改写才触发 `firstChangedDecision` 的修改型差异 → 模型调用挂钩自动下发 messages 区间 evict |
| chain3 会话终态       | `session.releaseKvc()` | evict + 解绑后，`getKvCacheRuntime()` 为空、再次 suspend 为终态 no-op |
| chain4 sticky 子代理 | TaskTool 派生 `verification_agent`（sticky）与 `code`（非 sticky） | 断言管理 edits 的父子缓存链接；`enableTaskLoop=true` 是子代理真正执行推理的前提 |

### 两处刻意为之的配置

- **`KVCacheConfig(5.0, 5.0, 5.0)`**：demo 的五条链路共享同一个单核心动作执行器，排队会导致动作启动晚于默认 2s 预算——超时会取消动作并置 fail-open，污染后续断言。调大预算让离线 demo 稳定复现调度语义（生产按需配置）。
- **`enableTaskLoop=true`**：DeepAgent 的 `invokeInternal` 在任务循环关闭时只返回占位结果、不执行推理；子代理 spec 必须打开它才能进入真实模型调用。

### 验证边界

mock 网关验证的是**协议正确性与调度语义**（hint 结构、edits 内容与次序、返回值），不能验证真实设备亲和与缓存命中——那需要对带 affinity 插件的真实网关运行，demo 已预留 `-Dopenjiuwen.example.affinityGateway` 切换能力（协议冒烟 demo 支持；调度 demo 的 hint 序列断言依赖内嵌 mock 记录）。

## 常见问题排查

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| 推理请求没有 `agent_hint` | 两个开关未同时满足：模型未声明 `extensions.kv_cache.mode=affinity`，或 `enableKvCacheAffinity=false` | 检查模型客户端配置与 Agent 开关 |
| 子代理推理 hint 的 `parent_session_id` 等于自身 | 任务循环使用自己的有效会话执行推理（已知缺口） | 管理动作的父子链接不受影响；关注后续修复 |
| 管理动作返回 `false`，后续 suspend/release 全部短路 | 某动作超时或失败置了 fail-open（设计语义：不影响推理） | 检查网关连通性与动作预算；调大 `KVCacheConfig` |
| `release` 之后再 suspend/release 无效果 | scope 已 TERMINAL 且绑定解绑（终态幂等设计） | 换新的会话 id |
| 日志出现 `unsupported KV affinity target` 或 range 校验失败 | 非法 target 或区间组合（校验失败即关闭，请求不会发出） | 修正动作参数（session 不接受区间、区间须 `0 <= start < end`） |
| 子代理收到调用但没有模型请求 | DeepAgent 未开 `enableTaskLoop`，`invokeInternal` 只返回占位结果 | 打开任务循环开关 |
| 日志出现 `fallback binding unavailable` | runtime 的 `bindingProvider` 返回 null | 检查 runtime 构造，兜底 provider 应返回 affinity 模型 |

## 日志观测点

| 日志关键字 | 级别 / 来源 | 含义 | 排障动作 |
| --- | --- | --- | --- |
| `agent_hint sent: session_id=..., action=...` | INFO / `llm` | 每个 affinity 请求（推理或管理）发出时打印完整 hint | grep 确认目标会话与 edits 内容、次序 |
| `KV cache <action> action failed: <原因>` | WARNING / `llm` | 管理动作 HTTP 失败（动作返回 false 的直接原因） | 检查网关连通性与端点 |
| `kvc action <kind> failed: ...` | WARNING / `KVCacheRuntime` | 模型动作反射调用失败或报告失败（fail-open 的来源之一） | 结合上下文定位被短路的动作 |
| `Ascend KV cache window diff eviction failed or returned false` | WARNING / `KVCacheModelCallHook` | 窗口差异驱逐失败（推理照常继续） | 检查网关与动作预算 |
| `Skip Ascend KV cache window diff eviction because session_id is empty` | WARNING / `KVCacheModelCallHook` | 会话无血缘，跳过窗口差异驱逐 | 检查 runtime 注入与会话 id |
| `KVC inference admission failed; continue inference` | WARNING / `KVCacheModelHook` | 准入失败（fail-open 放行） | 检查网关与动作预算 |
| `[KVCacheRuntime] fallback binding unavailable` | WARNING / `KVCacheRuntime` | 冷会话兜底 `bindingProvider` 返回 null | 检查 runtime 构造参数 |

排障套路：先 grep `agent_hint sent` 确认请求是否发出、内容是否正确；再按失败关键字定位是哪一层（客户端 HTTP / runtime 动作 / 挂钩驱逐）失败。

## 源码阅读地图

| 顺序 | 类 | 路径（`core/src/main/java`） | 建议关注点 |
| --- | --- | --- | --- |
| 1 | `AgentHint` | `com/openjiuwen/core/kvcache/AgentHint.java` | 线上协议构造与校验（失败即关闭） |
| 2 | `KVCacheRuntime` | `com/openjiuwen/core/kvcache/KVCacheRuntime.java` | 两级状态机、准入（`tryAcquireOnce`）、动作队列（`runAction`） |
| 3 | `KVCacheModelHook` / `KVCacheModelCallHook` | `com/openjiuwen/core/kvcache/`、`com/openjiuwen/core/singleagent/kvcache/` | 推理租约与窗口差异驱逐 |
| 4 | `AgentSession` | `com/openjiuwen/core/session/AgentSession.java` | `prepareKvc` / `suspendKvc` / `releaseKvc` 与 `getCacheIdentity` |
| 5 | `KVCacheSubagentLifecycle` / `KVCacheChildSession` | `com/openjiuwen/harness/kvcache/`、`com/openjiuwen/core/singleagent/kvcache/` | 子代理 sticky 生命周期与子会话继承 |
| 6 | `OpenAIModelClient` | `com/openjiuwen/core/foundation/llm/modelclients/OpenAIModelClient.java` | hint 注入与三个动作的 HTTP 实现 |
| 7 | 测试与示例 | `KVCacheRuntimeTest`、`KVCacheSubagentLifecycleTest`、`examples/kv-cache-demo` | 行为参照：先看测试断言再看实现 |

## 使用边界

- 网关必须实现 OpenAI 兼容端点并识别 `agent_hint`（`昇腾算力亲和` 插件）；对普通 OpenAI 端点发出管理请求会被当作普通补全处理。
- Ascend-affinity 与 InferenceAffinity 的 release 模式**互斥**：affinity 客户端 `supportsKvCacheRelease()` 恒为 false，二者不可同时启用。
- 已知缺口：`enableTaskLoop=true` 时任务循环使用自己的有效会话执行子代理推理，该推理的 `agent_hint` 为自指身份（父缓存链接丢失）；管理动作不受影响。默认 2s 动作超时在动作执行器共享、排队较深时偏紧，可能触发 fail-open，可按需调大 `KVCacheConfig`。
- 全链路默认关闭，需显式打开 `enableKvCacheAffinity` 并让模型声明 `mode=affinity`。
