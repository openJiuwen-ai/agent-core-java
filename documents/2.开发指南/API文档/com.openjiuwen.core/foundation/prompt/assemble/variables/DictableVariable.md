# com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable

## class DictableVariable

```java
public class DictableVariable extends Variable
```

结构化变量实现，用于递归处理 `Map`、`List` 及其中字符串值里的占位符。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DictableVariable(Object data, String name, String prefix, String suffix)` | 从嵌套数据结构中扫描占位符并初始化变量。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void update(Map<String, Object> kwargs)` | 深度复制原始 `Map` / `List` 结构后递归替换占位符。 |

## 使用说明

- 该类型会递归扫描 `Map` 与 `List` 中的字符串值。
- 点路径占位符与 `TextableVariable` 一样，按顶层键收集输入项。
- `Map` 的 key 不会参与占位符替换，只有 value 会被处理。
- `getName`、`setName`、`getInputKeys`、`getValue`、`eval` 由父类 `Variable` 提供。

## 相关测试

- `PromptAssembleTest`
