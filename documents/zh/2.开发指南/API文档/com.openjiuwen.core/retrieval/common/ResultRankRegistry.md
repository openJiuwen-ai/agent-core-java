# com.openjiuwen.core.retrieval.common.ResultRankRegistry

## 类 ResultRankRegistry

```java
public final class ResultRankRegistry
```

数据库原生 ranker 实现注册表，用于把数据库名称映射到对应的 weighted、RRF 或扩展 ranker 类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void registerResultRankerClass(String database, Class<?> weightedClass, Class<?> rrfClass, Map<String, Class<?>> extras)` | 注册某个数据库的 ranker 实现。 |
| `public static Class<?> getRankerClass(String database, String name)` | 查询指定数据库与名称对应的 ranker 类。 |
| `public static Map<String, Class<?>> getRankerClasses(String database)` | 返回某个数据库的全部 ranker 类映射。 |
