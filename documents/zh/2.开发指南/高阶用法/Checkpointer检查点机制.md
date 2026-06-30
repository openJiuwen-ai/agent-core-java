# Checkpointer 检查点机制

Checkpointer（检查点）是 openJiuwen 框架中用于管理 Agent 和工作流状态持久化和恢复的核心机制。它支持在关键执行节点保存状态，并在需要时恢复状态，从而实现中断恢复、异常恢复等功能。

## 核心概念

### 检查点的作用

Checkpointer 主要负责以下功能：

1. **状态持久化**：在 Agent 和工作流执行的关键节点保存状态
2. **状态恢复**：在重新执行时恢复之前保存的状态
3. **中断恢复**：支持工作流和 Agent 的中断-恢复机制
4. **异常恢复**：在发生异常时保存状态，便于后续恢复

### 命名空间结构

Checkpointer 使用命名空间来组织不同类型的状态：

- **`SESSION_NAMESPACE_AGENT`** (`"agent"`)：Agent 状态在会话下的命名空间
- **`SESSION_NAMESPACE_WORKFLOW`** (`"workflow"`)：工作流状态在会话下的命名空间（工作流自身状态）
- **`WORKFLOW_NAMESPACE_GRAPH`** (`"workflow-graph"`）：图状态在工作流下的命名空间（与工作流自身状态分离）

键的构建格式为：`sessionId:namespace:entityId:suffix`

## 检查点类型

openJiuwen 提供了多种检查点实现：

### 1. InMemoryCheckpointer（内存检查点）

基于内存的检查点实现，所有状态保存在内存中，进程重启后状态会丢失。适用于开发和测试场景。

**特点**：

- 无需额外配置
- 性能高，适合快速开发
- 数据不持久化，进程重启后丢失

**使用示例**：

```java
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

// 创建内存检查点实例
InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
CheckpointerFactory.setDefaultCheckpointer(checkpointer);

// 使用检查点进行状态管理
// checkpointer 会在 Agent 和工作流执行时自动保存和恢复状态
```

### 2. PersistenceCheckpointer（持久化检查点）

基于持久化存储的检查点实现，使用 `BaseKVStore` 接口进行状态持久化，支持任何实现了 `BaseKVStore` 的存储后端。

**支持的存储后端**：

- 文件存储
- 数据库存储

**配置示例**：

```java
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.spi.store.FileStore;

// 使用文件存储
FileStore fileStore = new FileStore("checkpointer.db");
PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(fileStore);
CheckpointerFactory.setDefaultCheckpointer(checkpointer);
```

### 3. RedisCheckpointer（Redis 检查点）

基于 Redis 的检查点实现，支持独立 Redis 和 Redis 集群模式。适用于生产环境，支持分布式部署。

**特点**：

- 支持独立 Redis 和 Redis 集群
- 支持 TTL（生存时间）配置
- 支持读取时刷新 TTL
- 适合分布式场景

**配置示例**：

```java
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;

// 独立 Redis
RedisCheckpointer checkpointer = new RedisCheckpointer(
        "redis://localhost:6379",
        null,  // token
        null   // 默认配置
);

// Redis 集群模式
RedisCheckpointer clusterCheckpointer = new RedisCheckpointer(
        "redis://localhost:7000",
        null,
        Map.of("cluster_mode", true)
);

CheckpointerFactory.setDefaultCheckpointer(checkpointer);
```

## 检查点生命周期

### Agent 检查点生命周期

Agent 检查点在以下时机进行状态管理：

1. **`preAgentExecute`**：Agent 执行前，恢复 Agent 状态
2. **`interruptAgentExecute`**：Agent 需要中断等待用户交互时，保存 Agent 状态
3. **`postAgentExecute`**：Agent 执行完成后，保存 Agent 状态

**执行流程**：

```text
开始执行 Agent
    ↓
preAgentExecute (恢复状态)
    ↓
执行 Agent 逻辑
    ↓
如果需要中断 → interruptAgentExecute (保存状态)
    ↓
执行完成 → postAgentExecute (保存状态)
```

### 工作流检查点生命周期

工作流检查点在以下时机进行状态管理：

1. **`preWorkflowExecute`**：工作流执行前，恢复或清理工作流状态
2. **`postWorkflowExecute`**：工作流执行后，保存或清理工作流状态

**执行流程**：

```text
开始执行工作流
    ↓
preWorkflowExecute
    ├─ 如果是 InteractiveInput → 恢复工作流状态
    └─ 如果不是 InteractiveInput → 检查状态
        ├─ 状态存在且未启用强制删除 → 抛出异常
        └─ 状态存在且启用强制删除 → 清理状态
    ↓
执行工作流逻辑
    ↓
postWorkflowExecute
    ├─ 发生异常 → 保存状态并抛出异常
    ├─ 正常完成 → 清理状态
    └─ 需要中断 → 保存状态
```

## 使用检查点

### 在 Runner 中配置检查点

Runner 是 openJiuwen 框架的核心执行器，在 Runner 启动时会自动初始化配置的检查点。这是推荐的使用方式，因为 Runner 会统一管理检查点实例，确保所有 Agent 和工作流使用相同的检查点配置。

#### 配置方式

通过 `RunnerConfig` 的 `checkpointerConfig` 字段配置检查点：

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import java.util.Map;

// 创建 Runner 配置
RunnerConfig runnerConfig = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "in_memory",  // 或 "persistence"、"redis"
                "conf", Map.of()
        ))
        .build();

// 设置 Runner 配置
Runner.setConfig(runnerConfig);

// 启动 Runner（会自动初始化检查点）
Runner.start();
```

#### 使用内存检查点

适用于开发和测试环境：

```java
RunnerConfig runnerConfig = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "in_memory",
                "conf", Map.of()
        ))
        .build();
Runner.setConfig(runnerConfig);
Runner.start();
```

#### 使用持久化检查点

适用于单机生产环境：

```java
RunnerConfig runnerConfig = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "persistence",
                "conf", Map.of(
                        "store_path", "checkpointer.db"
                )
        ))
        .build();
Runner.setConfig(runnerConfig);
Runner.start();
```

#### 使用 Redis 检查点

适用于分布式生产环境：

```java
RunnerConfig runnerConfig = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "redis",
                "conf", Map.of(
                        "connection", Map.of(
                                "url", "redis://localhost:6379"
                        )
                )
        ))
        .build();
Runner.setConfig(runnerConfig);
Runner.start();

// Redis 集群模式
RunnerConfig clusterConfig = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "redis",
                "conf", Map.of(
                        "connection", Map.of(
                                "url", "redis://localhost:7000",
                                "cluster_mode", true
                        )
                )
        ))
        .build();
Runner.setConfig(clusterConfig);
Runner.start();
```

### 手动管理检查点

你也可以手动管理检查点实例：

```java
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;

// 创建检查点
InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
CheckpointerFactory.setDefaultCheckpointer(checkpointer);

// 获取检查点
Checkpointer currentCheckpointer = CheckpointerFactory.getCheckpointer();

// 检查会话是否存在
boolean exists = currentCheckpointer.sessionExists("session_id");

// 释放会话资源
currentCheckpointer.release("session_id");

// 释放特定 Agent 的资源
currentCheckpointer.release("session_id", "agent_id");
```

## 中断恢复机制

Checkpointer 支持工作流和 Agent 的中断恢复机制。

### 工作流中断恢复

当工作流需要用户交互时，会触发中断并保存状态：

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.Map;

public class InterruptRecoveryExample {

    public static void workflowInterruptRecovery(Workflow workflow) throws Exception {
        String sessionId = "test_session";
        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());

        // 首次执行，触发中断
        WorkflowOutput output = workflow.invoke(
                Map.of("input", "test"),
                session,
                null
        );

        if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
            // 获取交互问题
            System.out.println("需要用户输入: " + output.getResult());

            // 提供用户回答
            InteractiveInput userInput = new InteractiveInput();
            userInput.update("questioner", "用户回答");

            // 恢复执行
            WorkflowOutput resumedOutput = workflow.invoke(userInput, session, null);
            System.out.println("恢复执行结果: " + resumedOutput.getResult());
        }

        // 清理检查点
        Runner.release(sessionId);
    }
}
```

### Agent 中断恢复

Agent 也支持中断恢复机制：

```java
// Agent 执行过程中触发中断
// 检查点会自动保存 Agent 状态

// 恢复执行时，检查点会自动恢复 Agent 状态
```

## 最佳实践

### 1. 选择合适的检查点类型

- **开发/测试环境**：使用 `InMemoryCheckpointer`，简单快速
- **单机生产环境**：使用 `PersistenceCheckpointer` 配合文件存储
- **分布式生产环境**：使用 `RedisCheckpointer`，支持集群模式

### 2. 状态清理

定期清理不再需要的状态：

```java
// 释放特定会话的资源
Runner.release("session_id");

// 释放特定 Agent 的资源
CheckpointerFactory.getCheckpointer().release("session_id", "agent_id");
```

### 3. 异常处理

检查点会在异常发生时自动保存状态，但你需要确保：

- 异常发生后能够正确恢复状态
- 定期清理过期或无效的状态
- 监控检查点存储的使用情况

## 故障排查

### 常见问题

1. **状态恢复失败**
   - 检查检查点配置是否正确
   - 检查存储后端是否正常运行
   - 检查会话 ID 是否正确

2. **状态未保存**
   - 检查检查点是否正确配置
   - 检查是否在正确的执行节点调用保存方法
   - 检查存储后端是否有写入权限

3. **状态冲突**
   - 确保同一会话 ID 不会并发执行
   - 检查是否有多个检查点实例同时操作同一会话

## 参考

- [Session 状态管理](./Session/状态管理.md)
- [Session 中断恢复](./Session/中断恢复.md)