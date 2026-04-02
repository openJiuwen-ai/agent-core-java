# com.openjiuwen.core.retrieval.common.TripleBeam

## class TripleBeam

```java
public class TripleBeam implements Iterable<RetrievalResult>
```

Beam of retrieval triples.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `triples` | `final List<RetrievalResult>` | triples. |
| `exists` | `final Set<String>` | exists. |
| `score` | `final double` | score. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TripleBeam(List<RetrievalResult> triples, double score)` | Create a new `TripleBeam` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public RetrievalResult get(int index)` | Execute `get`. |
| `public int size()` | Execute `size`. |
| `public boolean contains(RetrievalResult triple)` | Execute `contains`. |
| `public List<RetrievalResult> getTriples()` | Return the triples. |
| `public double getScore()` | Return the score. |
| `public Iterator<RetrievalResult> iterator()` | Execute `iterator`. |

## Notes

- Related tests: `RetrievalCoreTest.java`.
