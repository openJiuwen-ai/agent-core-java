# com.openjiuwen.core.workflow.internal.LegacyWorkflowComponentSupport

## 类 LegacyWorkflowComponentSupport

```java
public final class LegacyWorkflowComponentSupport
```

`LegacyWorkflowComponentSupport` 是旧式工作流组件兼容桥，用于把普通 POJO 适配成 `ComponentComposable`。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static ComponentComposable adapt(Object component)` | 若对象已实现 `ComponentComposable` 则直接返回，否则用反射适配 `invoke`、`stream`、`collect`、`transform`。 |

## 说明

- 缺少必需的 `invoke(inputs, session, context)` 方法时会抛 `UnsupportedOperationException`。
- 若返回值为 `CompletableFuture`，适配器会先 `join()` 再向上传递结果。
- `Workflow` 中接受 `Object component` 的兼容重载最终都会依赖该桥接层。
