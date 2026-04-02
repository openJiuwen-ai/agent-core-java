# com.openjiuwen.core.session.checkpointer.CheckpointerProvider

## 接口 CheckpointerProvider

```java
public interface CheckpointerProvider
```

`CheckpointerProvider` 是检查点构造入口的函数式接口，`CheckpointerFactory` 通过它把类型名映射到具体实现。

## 方法

| 签名 | 说明 |
| --- | --- |
| `Checkpointer create(Map<String, Object> conf)` | 根据配置映射创建并返回 `Checkpointer` 实例。 |

## 说明

- 接口带有 `@FunctionalInterface` 标记，可以直接使用 Lambda 注册。

```java
public interface CheckpointerProvider
```

-
