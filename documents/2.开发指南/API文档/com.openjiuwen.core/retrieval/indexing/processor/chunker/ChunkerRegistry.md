# com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry

## 类 ChunkerRegistry

```java
public final class ChunkerRegistry
```

`ChunkerRegistry` 管理按名称注册和获取 chunker 的工厂函数，既支持零参数 `Supplier`，也支持带 `Map<String, Object>` 参数的工厂。

## 默认注册项

- `char` -> `new CharChunker(512, 50)`
- `token` -> `new TokenizerChunker(512, 50)`
- `text` -> `new TextChunker(512, 50, "token")`
- `hybrid` -> `new HybridChunker(new TextChunker(512, 50, "token"))`

## 公开静态方法

- `registerChunker(String name, Supplier<Chunker> factory)`：注册零参数工厂。
- `registerChunker(String name, Function<Map<String, Object>, Chunker> factory)`：注册带参数工厂。
- `getChunker(String name)`：按默认空参数获取 chunker。
- `getChunker(String name, Map<String, Object> kwargs)`：按自定义参数获取 chunker；未注册时返回 `null`。

## 相关测试

- `TokenizerChunkerTest` 验证默认注册的 `hybrid` 名称会返回 `HybridChunker`。
