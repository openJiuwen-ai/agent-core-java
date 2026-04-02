# processor

`com.openjiuwen.core.retrieval.indexing.processor` contains the document-processing pipeline entry point plus child packages for chunking, extraction, parsing, and splitting.

## Modules

| Module | Description |
| --- | --- |
| [`chunker`](./processor/chunker.README.md) | contains preprocessors and chunking strategies that transform raw text into retrieval-ready chunks. |
| [`extractor`](./processor/extractor.README.md) | contains extractors that derive triples or other structured data during indexing. |
| [`parser`](./processor/parser.README.md) | contains parsers for files, web pages, spreadsheets, images, PDFs, and other raw content sources. |
| [`splitter`](./processor/splitter.README.md) | contains sentence-level and rule-based text splitters used by chunkers. |

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`Processor`](./processor/Processor.md) | `interface` | Generic retrieval processor abstraction. |

## Notes

- This package page links the documented child packages in the current retrieval subtree.
- The current page also links the 1 direct public type page(s) defined in this package.
