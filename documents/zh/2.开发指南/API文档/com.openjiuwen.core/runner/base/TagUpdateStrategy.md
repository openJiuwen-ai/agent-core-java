# com.openjiuwen.core.runner.base.TagUpdateStrategy

## 枚举 TagUpdateStrategy

```java
public enum TagUpdateStrategy
```

定义资源标签更新时的写入策略，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前标签更新策略对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `MERGE` | 将新标签与现有标签合并。 |
| `REPLACE` | 用新标签完全替换现有标签。 |
