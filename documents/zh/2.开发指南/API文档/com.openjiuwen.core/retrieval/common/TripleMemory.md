# com.openjiuwen.core.retrieval.common.TripleMemory

## 类 TripleMemory

```java
public class TripleMemory
```

三元组记忆容器，用于保存去重后的三元组集合。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public int size()` | 返回当前记忆条目数。 |
| `public List<List<String>> getMemory()` | 返回记忆副本。 |
| `public String getTriplesStr()` | 以字符串形式导出三元组列表。 |
| `public void extendMemory(List<String> triple)` | 加入单条三元组。 |
| `public void batchExtendMemory(List<List<String>> triples)` | 批量加入三元组。 |

## 说明

- 去重逻辑大小写不敏感。
