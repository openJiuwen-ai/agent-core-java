# indexing

`com.openjiuwen.core.retrieval.indexing` groups indexing backends and document-processing pipeline stages used to build searchable retrieval collections.

## Modules

| Module | Description |
| --- | --- |
| [`indexer`](./indexing/indexer.README.md) | contains index backend contracts and concrete indexers for in-memory, Milvus, and Chroma-style indexing. |
| [`processor`](./indexing/processor.README.md) | contains the document-processing pipeline entry point plus child packages for chunking, extraction, parsing, and splitting. |

## Notes

- This package page links the documented child packages in the current retrieval subtree.
