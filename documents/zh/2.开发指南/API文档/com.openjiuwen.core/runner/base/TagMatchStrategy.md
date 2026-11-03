# com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy

## 枚举 TagMatchStrategy

```java
public enum TagMatchStrategy
```

定义查询或过滤资源时多标签的匹配策略，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前标签匹配策略对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `ALL` | 资源必须同时包含所有指定标签。 |
| `ANY` | 资源只要包含任意一个指定标签即可。 |
