# com.openjiuwen.core.retrieval.common.Triple

## class Triple

```java
public class Triple
```

Knowledge triple.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `subject` | `String` | subject. |
| `predicate` | `String` | predicate. |
| `object` | `String` | object. |
| `confidence` | `Double` | confidence. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Triple()` | Create a new `Triple` instance. |
| `public Triple(String subject, String predicate, String object)` | Create a new `Triple` instance. |
| `public Triple(String subject, String predicate, String object, Double confidence, Map<String, Object> metadata)` | Create a new `Triple` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setSubject(String subject)` | Update the subject. |
| `public void setPredicate(String predicate)` | Update the predicate. |
| `public void setObject(String object)` | Update the object. |
| `public void setMetadata(Map<String, Object> metadata)` | Update the metadata. |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
- Related tests: `LLMTripleExtractorTest.java`, `RetrievalCoreTest.java`, `SimpleTripleExtractorTest.java`.
