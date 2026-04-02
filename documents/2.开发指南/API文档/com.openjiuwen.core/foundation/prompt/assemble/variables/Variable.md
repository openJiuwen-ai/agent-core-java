# com.openjiuwen.core.foundation.prompt.assemble.variables.Variable

## abstract class Variable

```java
public abstract class Variable
```

变量抽象基类。它封装变量名、依赖输入键与当前值，并统一 `eval` 的输入过滤流程。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `-` | 变量名称。 |
| `inputKeys` | `List<String>` | `[]` | 当前变量依赖的输入键列表；构造时传入 `null` 会转为空列表。 |
| `value` | `Object` | `""` | 当前变量值。 |

## 继承说明

- 构造方法为 `protected Variable(String name, List<String> inputKeys)`，该类型用于继承扩展而不是直接实例化。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getName()` | 返回变量名。 |
| `public void setName(String name)` | 更新变量名。 |
| `public List<String> getInputKeys()` | 返回输入键列表。 |
| `public Object getValue()` | 返回当前值。 |
| `public abstract void update(Map<String, Object> kwargs)` | 由子类实现具体更新逻辑。 |
| `public Object eval(Map<String, Object> kwargs)` | 先按 `inputKeys` 过滤输入，再调用 `update` 并返回当前值。 |

## 相关测试

- `PromptAssembleTest`
