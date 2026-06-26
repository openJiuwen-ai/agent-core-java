# com.openjiuwen.core.controller.legacy.constants.IntentDetectionConstants

## final class IntentDetectionConstants

```java
public final class IntentDetectionConstants
```

`IntentDetectionConstants` 定义旧版意图检测流程中的模板字段名和角色名映射。

## 常量

| 常量 | 值 | 说明 |
|---|---|---|
| `USER_PROMPT` | `user_prompt` | 用户提示键。 |
| `CATEGORY_LIST` | `category_list` | 分类列表键。 |
| `DEFAULT_CLASS` | `default_class` | 默认分类键。 |
| `ENABLE_HISTORY` | `enable_history` | 是否启用历史的键。 |
| `ENABLE_INPUT` | `enable_input` | 是否启用当前输入的键。 |
| `EXAMPLE_CONTENT` | `example_content` | 示例内容键。 |
| `CHAT_HISTORY_MAX_TURN` | `chat_history_max_turn` | 历史轮数上限键。 |
| `CHAT_HISTORY` | `chat_history` | 历史消息键。 |
| `INPUT` | `input` | 当前输入键。 |

## 角色映射

`ROLE_MAP` 把模型消息角色映射为中文标签：`user -> 用户`、`assistant -> 助手`、`system -> 系统`。

## 说明

- 该类是纯常量工具类，私有构造函数禁止外部实例化。
- `DefaultIntentDetector.prepareDetectionInput()` 会直接使用这些键名组织输入映射。
