# com.openjiuwen.core.retrieval.common.BaseRankConfig

## class BaseRankConfig

```java
public abstract class BaseRankConfig
```

Base type for result-ranker configuration.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `name` | `final String` | name. |
| `higherIsBetter` | `final boolean` | higher is better. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected BaseRankConfig(String name, boolean higherIsBetter)` | Create a new `BaseRankConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getName()` | Return the name. |
| `public boolean isHigherIsBetter()` | Return whether higher is better. |
| `public abstract RankerArguments getArgs()` | Return the args. |
| `public List<Integer> isActive()` | Return whether active. |
| `public Class<?> getRankerClass(String database)` | Return the ranker class. |

## Nested Types

| Type | Kind | Description |
| --- | --- | --- |
| `RankerArguments` | `record` | Ranker constructor arguments. |
