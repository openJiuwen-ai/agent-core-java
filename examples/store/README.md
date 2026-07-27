# Store Extension Baseline

The Java-side `extensions.store` baseline now exposes:

- `GaussDbStore`
- `ElasticsearchVectorStore`
- factory routing for `elasticsearch` / `es`

The current Elasticsearch adapter is compatibility-first and reuses the local retrieval backend until a full Java Elasticsearch implementation is ported.
