# com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable

## class TextableVariable

```java
public class TextableVariable extends Variable
```

字符串变量实现，用于解析纯文本中的占位符并完成替换。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TextableVariable(String text, String name, String prefix, String suffix)` | 从模板文本中扫描占位符并初始化变量。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void update(Map<String, Object> kwargs)` | 根据传入参数替换占位符，并更新当前值。 |

## 使用说明

- 空占位符会触发异常。
- 点路径占位符如 `{{user.name}}` 会把顶层 `user` 记为输入键，并在运行时继续沿 getter 或 `Map` 键访问具体值。
- 数字、布尔值和其他对象都会在替换时转成字符串。
- `getName`、`setName`、`getInputKeys`、`getValue`、`eval` 由父类 `Variable` 提供。

## 相关测试

- `PromptAssembleTest`
