# DeepAgent Java Example（Todo 与 Checkpointer 共享 Redis）

这个目录演示如何真实调用 `DeepAgent`，并让其内部的 **Todo**（任务规划存储）与 **Checkpointer**（会话状态检查点）共用同一份 Redis 连接，把两份数据都写入 Redis。

> 版本说明：本示例依赖的能力（`DeepAgentConfig.kvStoreConfig`、`todoStorageType("kv")`、`redis_client` 复用、`DeepAgent.ensureInitialized()` 提前触发等）自 **agent-core-java 0.1.14** 起支持。请确认 `pom.xml` 中 `<version>0.1.14</version>`（或更高版本）后再运行。

## 文件说明

- `DeepAgentRedisExample.java`: 示例入口。创建共享 `JedisPooled`、注册 `RedisCheckpointer`、构建 `DeepAgent`（通过 `kvStoreConfig.conf.redis_client` 复用同一连接）、发起一次真实 `invoke`，并验证 Checkpoint 与 Todo 都落盘 Redis。
- `DeepAgentExampleConfigLoader.java`: 读取 LLM 与 Redis 配置。优先从环境变量读取，回退到 `examples/apiconfig.json`。
- `../apiconfig.json`:（可选）回退配置文件；环境变量未设置时使用。

## 配置

运行前，请把下列信息配置为**环境变量**（示例优先读取环境变量，未设置时回退到 `examples/apiconfig.json`）：

| 环境变量 | 说明 |
|---------|------|
| `API_BASE` | 大模型 API 地址，例如 `http://your-host:8080/v1` |
| `API_KEY` | 大模型 API Key（请填入你自己的密钥） |
| `MODEL_PROVIDER` | 客户端类型，例如 `OpenAI` |
| `MODEL_NAME` | 模型名，例如 `GLM-5.1` |
| `LLM_SSL_VERIFY` | 是否校验 SSL，`False` 表示关闭 |
| `REDIS_HOST` | Redis 主机，例如 `127.0.0.1` |
| `REDIS_PORT` | Redis 端口，例如 `6379` |

PowerShell 设置示例（请把 `<your-api-key>` 换成你自己的 Key）：

```powershell
$env:API_BASE = "http://your-host:8080/v1"
$env:API_KEY = "<your-api-key>"
$env:MODEL_PROVIDER = "OpenAI"
$env:MODEL_NAME = "GLM-5.1"
$env:LLM_SSL_VERIFY = "False"
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
```

> 也可以把这些值写进 `examples/apiconfig.json`（在根键下追加 `REDIS_HOST` / `REDIS_PORT`）。环境变量优先级最高。

## 运行前提

1. Redis 服务已启动并可访问（示例会用 `PING` 探测一次）。
2. `examples/apiconfig.json` 中的模型配置（或环境变量）已填入真实可用的值。
3. Jedis 5.1.5 在 classpath。`agent-core-java` 把 Jedis 声明为 `test` 作用域（不传递），因此下面的命令用 `dependency:copy-dependencies -DincludeScope=test` 把 Jedis 及其传递依赖（`commons-pool2`、`json` 等）一起取出来。

## 运行方式

以下命令假设当前目录是 Java 仓库根目录，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。建议先执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:copy-dependencies "-DoutputDirectory=target/dependency" "-DincludeScope=test" -q
javac -encoding UTF-8 -source 17 -target 17 -cp "target/classes;target/dependency/*" -d examples/deep_agent/build examples/deep_agent/DeepAgentExampleConfigLoader.java examples/deep_agent/DeepAgentRedisExample.java
java "-Dfile.encoding=UTF-8" -cp "examples/deep_agent/build;target/classes;target/dependency/*" examples.deep_agent.DeepAgentRedisExample
```

> 注意：在 **PowerShell** 中，`-Dfile.encoding=UTF-8` 必须用双引号包起来（如上），否则 PowerShell 会把它拆成多个 token，导致 `java` 报 `找不到或无法加载主类 .encoding=UTF-8`。cmd.exe 与 bash 不受此影响。
>
> 说明：`-DincludeScope=test` 用于把 `test` 作用域的 Jedis（及其传递依赖）也拷到 `target/dependency`，这样编译期（示例直接 `import redis.clients.jedis.JedisPooled`）与运行期都能找到 Jedis。

## 输出

示例最终会打印每个步骤的执行情况，关键内容如下：

- `[2] 共享 JedisPooled 已创建, PING -> PONG`：共享 Redis 连接就绪。
- `[3] RedisCheckpointer 已注册为默认 checkpointer`：Checkpointer 路径已桥接到同一 Redis。
- `[5] DeepAgent.ensureInitialized() 已触发, kvStore=RedisStore`：Todo 路径也复用了同一 RedisStore。
- `[6] Agent 输出: ...`：真实 DeepAgent 调用返回的回答。
- `[7] Checkpointer 已写入 (N 个 key)`：形如 `deep_agent_example:{sessionId}:agent:deep_agent_redis_example:agent_state_blobs*`。
- `[7] Todo 已写入 (1 个 key)`：形如 `deep_agent_example:{sessionId}:todo`，并打印任务清单内容。
- `[7] 共享结论: Checkpointer 与 Todo 均写入同一 Redis 实例 ...`：验证通过。

## 关键机制

1. **共享 JedisPooled 是核心**：启动期创建一个线程安全的 `JedisPooled(host, port)`，Checkpointer 和 Todo 两条路径都包装它，连同一 Redis。
2. **`redis_client` 字段复用**：通过 `kvStoreConfig.conf.redis_client = jedisPooled` 让框架 SPI `RedisKVStoreProvider` 直接复用该连接，而不是反射创建一个非线程安全的单 `Jedis`，避免孤儿连接。
3. **Checkpointer 路径**：`new RedisCheckpointer(sharedRedisStore, Map.of())` 包装同一 `RedisStore`，并通过 `CheckpointerFactory.setDefaultCheckpointer(...)` 注册为全局默认；`DeepAgent.invoke` 内部的 `preRun/postRun` 会触发它，把会话状态写入 Redis。
4. **Todo 路径**：`enableTaskPlanning(true) + todoStorageType("kv")` 让 `TaskPlanningRail` 把 todo 存到 `agent.kvStore`（即同一 `RedisStore`）；示例提示词显式引导 LLM 调用 `todo_create`。
5. **手动 `ensureInitialized()`**：提前触发懒初始化，保证 `TaskPlanningRail.init` 在 `kvStore` 已注入后执行，Todo 走共享路径而非 InMemory 回退。
6. **Key 命名**（`enableTenantIsolation(true)` 下）：
   - Checkpointer：`{tenantId}:{sessionId}:agent:{agentId}:agent_state_blobs` 及 `..._dump_type`
   - Todo：`{tenantId}:{sessionId}:todo`
   两者前缀同为 `{tenantId}:{sessionId}:`，共存不冲突。

## 说明

- 示例不缓存 Agent，运行结束在 `finally` 中清理：重置全局 checkpointer、删除 `{tenantId}:` 前缀的演示 key、关闭 `DeepAgent` 与 `JedisPooled`、`Runner.stop()`。
- 工作目录使用系统临时目录（`Files.createTempDirectory`），不会污染示例目录；多租户隔离会在其下创建 `tenants/{tenantId}/`。
- 提示词显式给出 `session_id` 与 `tasks` JSON，确保 LLM 可靠调用 `todo_create`，便于演示验证。
