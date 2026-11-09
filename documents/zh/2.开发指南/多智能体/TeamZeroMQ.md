# Team ZeroMQ 配置

本文说明 Core `com.openjiuwen.agentteams` 的传输装配。它不依赖 Swarm、Gateway 或 WebUI。
Java 端使用随 Core 引入的 JeroMQ；仅运行 Java 成员不需要安装 Python `pyzmq` SDK。
`transport="pyzmq"` 是现有兼容名称，默认 `inprocess` 传输不变。

## 同一 JVM 的团队

在已有的模型池配置上选择 ZeroMQ：

```java
import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;

LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
        .teamName("core_zmq_demo")
        .spawnMode("inprocess")
        .transportConfig(MessagerTransportConfig.builder()
                .backend("pyzmq")
                .requestTimeout(10.0)
                .build())
        .storage("memory")
        .modelPool(modelPool)
        .build();
```

`modelPool` 应使用项目已有的模型配置。也可保留原有 `.transport("pyzmq")` 写法。
未显式指定端点时，Leader 创建仅监听 `127.0.0.1`、使用临时端口的 XSUB/XPUB 代理。
每个成员持有独立的 direct socket；同进程 spawn 显式共享网络所有者，并交换真实 peer 地址。
不同团队不会共用代理或全局网络注册表。

成员在开始协调时注册 direct handler，并订阅会话范围的团队、任务、消息主题。
这里的“订阅”是接收事件，与购买服务无关。订阅 Future 在私有探针实际穿过 PUB/SUB 网络后完成；
探针不会交给业务 handler，也不重试业务任务。代理不可达时，按 `requestTimeout` 明确失败。

执行结束后调用 `team.agent().close()`。成员退出会释放自己的端口；最后一个本地成员关闭后释放代理。
提交线程池失败或队列中取消的成员也会释放已分配的传输资源。

## 显式端点与外部代理

`MessagerTransportConfig` 可通过 `LeaderTeammateAgentTeam.Builder.transportConfig(...)`
或 `TeamAgentSpec.transportConfig` 传入；配置在装配时复制，运行时绑定端口不会修改调用方的配置。
未填写旧 `transport` 字段时，从配置的 `backend` 推导；显式填写时仍以旧字段为准。

| 字段 | 用途 |
| --- | --- |
| `directAddr` | 当前节点 ROUTER 的绑定地址 |
| `pubsubPublishAddr` | PUB 连接的代理 XSUB 地址 |
| `pubsubSubscribeAddr` | SUB 连接的代理 XPUB 地址 |
| `knownPeers` / `bootstrapPeers` | direct 路由，包含对方 `agentId` 和可达 `addrs` |
| `requestTimeout` | direct ACK 和订阅就绪的超时秒数，须为有限正数 |
| `metadata.pubsub_bind` | Leader 是否拥有代理；设为 `false` 时连接外部代理 |

独立部署示意：

```java
MessagerTransportConfig config = MessagerTransportConfig.builder()
        .backend("pyzmq")
        .directAddr("tcp://127.0.0.1:35601")
        .pubsubPublishAddr("tcp://127.0.0.1:35602")
        .pubsubSubscribeAddr("tcp://127.0.0.1:35603")
        .metadata(java.util.Map.of("pubsub_bind", false))
        .build();
```

该示意要求外部代理已经运行，还需按部署拓扑填写双方的 peer 配置。
跨主机部署必须提供真实可达地址，不能把回环地址或未解析的通配端口发给其他主机。
首期配置校验支持 TCP 端点；不会自动启动远程代理、发现公网地址、配置认证或开放防火墙。
不应把无认证的端点暴露到不受信任网络。

Spawn payload 保存已解析的代理端点、存储和运行选项，JSON 也接受 `direct_addr`、
`pubsub_publish_addr`、`pubsub_subscribe_addr`、`known_peers` 等 snake_case 别名。
序列化配置不等于自动完成跨进程的双向 peer 发现；独立成员仍需明确的可达 direct 地址及双向路由。

## 验证范围

- Core 网络测试覆盖首条事件、direct 请求响应、团队隔离、重复成员、无效端点、代理不可达和端口释放。
- Core 装配测试覆盖公开 Builder、spawn JSON 恢复、线程池拒绝和运行前取消的资源释放。
- 测试仓 Team002 使用 `pyzmq + inprocess spawn + SQLite + temporary + build_mode`，
  由确定性模型响应驱动真实 Core 工具、成员和数据库，验证创建、执行、回报和结束。
- 这不代表 Swarm 已接入 ZeroMQ，也不代表多主机部署已通过端到端验收。
