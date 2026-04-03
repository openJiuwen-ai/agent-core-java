# com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser

## 类 ExcelParser

```java
public class ExcelParser extends Parser
```

`ExcelParser` 负责解析 `xlsx`、`csv`、`tsv` 等表格文件，并把表格内容拆成“按行”和“按列”两类 `Document`。

## 公开静态方法

- `cellStr(Object value)`：把单元格值转成去空白字符串。
- `rowsToDocuments(...)`：根据表格二维数据生成 `Document` 列表。

## 行列文档规则

- 行文档 metadata 包含 `sheet_name`、`row_index`、`source_type = "row"`。
- 列文档 metadata 包含 `sheet_name`、`column_name`、`source_type = "column"`。
- `includeHeader = true` 时，行文档会以 `header: value` 形式拼接，列文档会生成 `Column name: ... Values: ...` 文本。

## 公开方法

- `parse(...)`：文件不存在时抛 `RETRIEVAL_INDEXING_FILE_NOT_FOUND`；解析失败时抛 `RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT`。
- `supports(String doc)`：支持 `.xlsx`、`.csv`、`.tsv`。

## 选项

- `include_header` 或 `includeHeader`：控制是否把表头拼入输出文本，默认 `true`。
