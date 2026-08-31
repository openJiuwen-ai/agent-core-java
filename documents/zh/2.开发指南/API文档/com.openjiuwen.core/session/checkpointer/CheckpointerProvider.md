# com.openjiuwen.core.session.checkpointer.CheckpointerProvider

## 接口 CheckpointerProvider

```java
public interface CheckpointerProvider
```

`CheckpointerProvider` 是检查点构造入口，`CheckpointerFactory` 通过它把类型名映射到具体的 `Checkpointer` 实现。Provider 只负责根据配置创建实例，不负责在每次保存时处理 session 数据。

## 方法

| 签名 | 说明 |
| --- | --- |
| `String typeName()` | 返回该 Provider 支持的检查点类型名。 |
| `Checkpointer create(Map<String, Object> conf)` | 根据配置映射创建并返回 `Checkpointer` 实例。 |

## 说明

- Provider 可以通过 `ServiceLoader` 注册，也可以调用
  `CheckpointerFactory.register("custom_type", provider)` 编程注册。
- 需要选择性保存、脱敏或限制 checkpoint 大小时，应自定义 `Checkpointer`，在其保存生命周期中处理数据；仅实现 Provider
  不会改变内置 Checkpointer 的序列化行为。

## 注册自定义 Provider

下面的示例使用 `sanitized` 作为自定义类型名。`create(...)` 只读取配置并组装自定义 Checkpointer；字段筛选和脱敏逻辑应放在
`SanitizedCheckpointer` 的保存方法中。

```java
public final class SanitizedProvider implements CheckpointerProvider {
    @Override
    public String typeName() {
        return "sanitized";
    }

    @Override
    public Checkpointer create(Map<String, Object> conf) {
        BaseKVStore kvStore = (BaseKVStore) conf.get("kv_store");
        return new SanitizedCheckpointer(kvStore, conf);
    }
}

CheckpointerFactory.register("sanitized", new SanitizedProvider());
```

也可以在扩展 JAR 中创建
`META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider`，每行写入一个 Provider 的完整类名，
由 `ServiceLoader` 自动发现。

## 自定义 Checkpointer 的处理边界

`SanitizedCheckpointer` 应继承 `Checkpointer` 并实现完整生命周期。保存时可以按以下顺序处理：

1. 在 `interruptAgentExecute`、`postAgentExecute` 或 workflow 的保存入口读取 `session.state().getState()`。
2. 先复制状态，递归遍历嵌套的 `Map`、`List` 等结构，仅保留恢复所需字段；对用户消息、Tool 返回值、模型响应等内容按业务规则过滤、替换、哈希或截断。
3. 序列化前检查单个 checkpoint 的字节数和必要的会话累计量，超限时按业务策略拒绝、裁剪或转存到其他介质。
4. 将处理后的数据写入业务方提供的 `BaseKVStore`、Redis 或对象存储。
5. 在 `preAgentExecute`、`preWorkflowExecute` 中读取并反序列化，按自定义存储格式完成必要的格式转换，再通过
   `session.state().setState(...)` 恢复运行态。单向哈希、删除等不可逆处理不应被设计为可还原数据。

实现 `graphStore()` 时还要提供 workflow 图恢复所需的 `Store`；如果只保存 agent state 而忽略 workflow updates 或 graph state，
中断恢复可能无法继续。`sessionExists` 和 `release` 也应与自定义的键结构保持一致。

内置 `PersistenceCheckpointer` 的序列化器是实现内部固定配置，不能仅通过继承或 Provider 配置注入过滤器。需要改变保存内容或
序列化方式时，应完整实现自定义 `Checkpointer`（可以复用底层 `BaseKVStore`）。
