# chunker

`com.openjiuwen.core.retrieval.indexing.processor.chunker` contains preprocessors and chunking strategies that transform raw text into retrieval-ready chunks.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`CharChunker`](./chunker/CharChunker.md) | `class` | Character window chunker. |
| [`CharSplitterText`](./chunker/CharSplitterText.md) | `class` | Simple text splitter based on character length, no dependency on tokenizer. |
| [`Chunker`](./chunker/Chunker.md) | `class` | Chunker abstraction for documents. |
| [`ChunkerRegistry`](./chunker/ChunkerRegistry.md) | `class` | Registry for named chunkers. |
| [`HybridChunker`](./chunker/HybridChunker.md) | `class` | Chunker that skips splitting for specific document types. |
| [`IndexSentenceSplitter`](./chunker/IndexSentenceSplitter.md) | `class` | SentenceSplitter wrapper with sentence splitting capabilities. |
| [`PreprocessingPipeline`](./chunker/PreprocessingPipeline.md) | `class` | Sequential text preprocessing pipeline. |
| [`SpecialCharacterNormalizer`](./chunker/SpecialCharacterNormalizer.md) | `class` | Replaces control characters with spaces. |
| [`TextChunker`](./chunker/TextChunker.md) | `class` | Composite chunker with preprocessing. |
| [`TextPreprocessor`](./chunker/TextPreprocessor.md) | `interface` | Text preprocessor abstraction. |
| [`TextSplitter`](./chunker/TextSplitter.md) | `class` | Abstract base class for text splitters. |
| [`TokenizerChunker`](./chunker/TokenizerChunker.md) | `class` | Token-aware chunker backed by SentenceSplitter. |
| [`URLEmailRemover`](./chunker/URLEmailRemover.md) | `class` | Removes URLs and email addresses from text. |
| [`WhitespaceNormalizer`](./chunker/WhitespaceNormalizer.md) | `class` | Normalizes repeated whitespace. |

## Notes

- The current page also links the 14 direct public type page(s) defined in this package.
