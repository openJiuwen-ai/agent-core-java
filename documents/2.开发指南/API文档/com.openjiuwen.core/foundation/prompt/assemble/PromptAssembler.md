# com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler

## class PromptAssembler

```java
public class PromptAssembler
```

模板装配器。它从模板内容中提取输入键，校验预置变量，并按占位符规则输出装配后的字符串或消息列表。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix, Map<String, Variable> initialVariables)` | 以模板内容、分隔符和预置变量初始化装配器。 |
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix)` | 不传预置变量的便捷构造。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> getInputKeys()` | 返回当前模板需要的输入键，按出现顺序去重。 |
| `public Object promptAssemble(Map<String, Object> kwargs)` | 执行占位符替换，返回装配后的 `String` 或 `List<BaseMessage>`。 |

## 使用说明

- 支持的模板内容为 `String` 或 `List<BaseMessage>`。
- 对消息列表来说，只有字符串内容和首元素为 `Map` 的非空列表内容会被格式化。
- 预置变量的名称必须已经在模板中出现，且值必须是 `Variable` 子类实例。
- `promptAssemble` 会过滤无关键，并把缺失键补为原始占位符文本。

## 相关测试

- `PromptAssembleTest`
