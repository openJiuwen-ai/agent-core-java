# query

`com.openjiuwen.core.foundation.store.query` 提供 Chroma 与 Milvus 的查询方言定义，以及一次性注册入口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ChromaQueryDialect`](query/ChromaQueryDialect.md) | 生成 Chroma 风格的过滤 `Map`。 |
| [`MilvusQueryDialect`](query/MilvusQueryDialect.md) | 生成 Milvus 风格的查询表达式。 |
| [`QueryDialectRegistration`](query/QueryDialectRegistration.md) | 把内建方言注册到 `QueryLanguageRegistry`。 |
