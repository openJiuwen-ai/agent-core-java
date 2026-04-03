# com.openjiuwen.core.workflow.WorkflowCard

## 类 WorkflowCard

```java
public class WorkflowCard extends BaseCard
```

`WorkflowCard` 保存工作流元信息，包括 id、name、version、description 以及输入参数定义。

## 关键字段

| 字段 | 说明 |
| --- | --- |
| `version` | 工作流版本。 |
| `inputParams` | 输入参数定义，可为 `Map` 或 `Class<?>`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowCard(String id, String name)` | 以最小元信息构造工作流卡片。 |
| `public WorkflowCard(String id, String name, String version, String description)` | 兼容旧测试位置参数风格。 |
| `public Object toolInfo()` | 把工作流卡片转换为 `ToolInfo`。 |
| `public String str()` | 返回字符串表示。 |

## 说明

- `WorkflowTest` 通过 `inputParams` 覆盖了输入校验相关行为。
