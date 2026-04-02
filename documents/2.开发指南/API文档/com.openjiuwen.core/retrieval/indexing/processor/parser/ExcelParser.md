# com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser

## class ExcelParser

```java
public class ExcelParser extends Parser
```

Parser for xlsx/csv/tsv tabular files that emits row and column documents.

## Methods

| Signature | Description |
| --- | --- |
| `public static String cellStr(Object value)` | Execute `cellStr`. |
| `public static List<Document> rowsToDocuments(List<? extends List<?>> rows, String sheetName, String baseId, int sheetIndex, boolean includeHeader)` | Execute `rowsToDocuments`. |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | Parse the input into `Document` records. |
| `public boolean supports(String doc)` | Return whether this implementation can handle the input. |

## Notes

- Related tests: `ExcelParserTest.java`.
