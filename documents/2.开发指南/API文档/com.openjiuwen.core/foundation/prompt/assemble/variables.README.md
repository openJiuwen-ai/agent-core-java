# variables

`com.openjiuwen.core.foundation.prompt.assemble.variables` 定义模板变量的公共抽象，以及面向纯文本、`Map`/`List` 嵌套结构的两种变量实现。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`Variable`](variables/Variable.md) | 变量抽象基类，统一输入过滤与 `eval` 调用流程。 |
| [`TextableVariable`](variables/TextableVariable.md) | 面向字符串模板的占位符解析与替换实现。 |
| [`DictableVariable`](variables/DictableVariable.md) | 面向 `Map` / `List` 结构的递归占位符替换实现。 |

## 说明

- 点路径占位符如 `{{user.name}}` 只会把顶层 `user` 记为输入键。
- `Variable` 适合继承扩展，不是直接面向外部实例化的基础类型。
