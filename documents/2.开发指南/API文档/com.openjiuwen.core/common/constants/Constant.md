# com.openjiuwen.core.common.constants.Constant

## class Constant

```java
public final class Constant
```

`Constant` 汇总框架范围内共享的字符串键与安全上限常量。

## 常量字段

| 字段 | 类型 | 值 | 说明 |
| --- | --- | --- | --- |
| `USER_FIELDS` | `String` | `"userFields"` | IR 中的用户字段键。 |
| `QUERY` | `String` | `"query"` | 查询字段键。 |
| `SYSTEM_FIELDS` | `String` | `"systemFields"` | IR 中的系统字段键。 |
| `INTERACTION` | `String` | `"__interaction__"` | 工作流交互标记。 |
| `INTERACTIVE_INPUT` | `String` | `"__interactive_input__"` | 节点抛出的动态交互输入标记。 |
| `INPUTS_KEY` | `String` | `"inputs"` | 输入集合键名。 |
| `CONFIG_KEY` | `String` | `"config"` | 配置集合键名。 |
| `END_FRAME` | `String` | `"all streaming outputs finish"` | 全部流式输出结束标记。 |
| `END_NODE_STREAM` | `String` | `"end node stream"` | 单节点流结束标记。 |
| `LOOP_ID` | `String` | `"__sys_loop_id"` | 系统循环 ID 键。 |
| `INDEX` | `String` | `"index"` | 索引键。 |
| `FINISH_INDEX` | `String` | `"finish_index"` | 结束索引键。 |
| `MAX_COLLECTION_SIZE` | `int` | `100000` | 集合安全上限。 |
| `MAX_EXPRESSION_LENGTH` | `int` | `5000` | 表达式长度安全上限。 |
| `MAX_AST_DEPTH` | `int` | `50` | AST 深度安全上限。 |
| `NESTED_LOOP_DEPTH` | `int` | `1` | 嵌套循环深度上限。 |

## 说明

- 该类型仅包含静态常量，私有构造器用于阻止实例化。
