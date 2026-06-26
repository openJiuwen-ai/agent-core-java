# com.openjiuwen.core.foundation.store.graph.BaseRankConfig

## 类 BaseRankConfig

```java
public abstract class BaseRankConfig
```

结果融合配置基类，统一 ranker 名称、分数方向与参数导出接口。

## 嵌套类型

| 类型 | 说明 |
| --- | --- |
| `RankerArguments` | 记录 ranker 的位置参数与关键字参数。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getName()` | 返回 ranker 名称。 |
| `public boolean isHigherIsBetter()` | 返回分数方向。 |
| `public abstract RankerArguments getArgs()` | 导出底层 ranker 参数。 |
| `public List<Integer> isActive()` | 返回各通道是否启用，默认值为 `[1, 1, 1]`。 |
| `public Class<?> getRankerClass(String database)` | 根据数据库名称查询已注册的 ranker 实现。 |

## 说明

- 具体子类会通过 `ResultRankRegistry` 解析数据库原生 ranker 实现。
