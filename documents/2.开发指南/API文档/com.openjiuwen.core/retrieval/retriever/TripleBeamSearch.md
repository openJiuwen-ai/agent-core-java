# com.openjiuwen.core.retrieval.retriever.TripleBeamSearch

## class TripleBeamSearch

```java
public class TripleBeamSearch
```

Triple beam search used by graph retrieval.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `retriever` | `final Retriever` | Retriever used to fetch candidate triples for beam expansion. |
| `numBeams` | `final int` | Maximum number of beams kept after each scoring pass. |
| `numCandidatesPerBeam` | `final int` | Number of candidate triples retrieved per beam-expansion step. |
| `maxLength` | `final int` | Maximum triple-chain length. |
| `embedModel` | `final Embedding` | Embedding model taken from an `AbstractStoreBackedRetriever` when available. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TripleBeamSearch(Retriever retriever)` | Create a beam-search helper with defaults `numBeams = 10`, `numCandidatesPerBeam = 100`, and `maxLength = 2`. |
| `public TripleBeamSearch(Retriever retriever, int numBeams, int numCandidatesPerBeam, int maxLength)` | Create a beam-search helper with explicit beam width, candidate count, and path length. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<TripleBeam> beamSearch(String query, List<RetrievalResult> triples)` | Embed the query and seed triples, rank the initial beams by cosine similarity, iteratively expand linked triples, and return the best distinct beams. |

## Notes

- The constructor requires `maxLength >= 1`, and `beamSearch(...)` requires an embedding-capable store-backed retriever.
- Related tests: `RetrievalCoreTest.java`.
