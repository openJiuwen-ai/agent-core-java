# extract

`com.openjiuwen.core.memory.process.extract` contains extract-time parameter models, analyzers, generators, and LLM-backed memory extraction helpers.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`ExtractMemoryParams`](./extract/ExtractMemoryParams.md) | class | Parameters for memory extraction. |
| [`Generator`](./extract/Generator.md) | class | Generates all memory units (variables, summary, fragment) from conversation messages. |
| [`LongTermMemoryExtractor`](./extract/LongTermMemoryExtractor.md) | class | Extracts long-term memory (fragment memories) from conversation using LLM. |
| [`MemoryAnalyzer`](./extract/MemoryAnalyzer.md) | class | Analyzes conversation messages to determine key information, extract variables, and generate summary. |
| [`MemoryAnalyzerResult`](./extract/MemoryAnalyzerResult.md) | class | Result of memory analysis containing key information flag, variables, and summary. |
| [`VariableResult`](./extract/VariableResult.md) | class | Result of variable extraction from memory analysis. |

## Notes

- The current page also links the 6 direct public type page(s) defined in this package.
