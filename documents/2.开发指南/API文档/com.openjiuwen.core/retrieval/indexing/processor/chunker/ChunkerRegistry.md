# com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry

## class ChunkerRegistry

```java
public final class ChunkerRegistry
```

Registry for named chunkers.

## Methods

| Signature | Description |
| --- | --- |
| `public static void registerChunker(String name, Supplier<Chunker> factory)` | Register a chunker with a zero-arg supplier (convenience overload). |
| `public static void registerChunker(String name, Function<Map<String, Object>, Chunker> factory)` | Register a chunker with a parameterized factory accepting kwargs. |
| `public static Chunker getChunker(String name)` | Get a chunker by name using default parameters. |
| `public static Chunker getChunker(String name, Map<String, Object> kwargs)` | Get a chunker by name, passing kwargs to the factory. |

## Notes

- Related tests: `TokenizerChunkerTest.java`.
