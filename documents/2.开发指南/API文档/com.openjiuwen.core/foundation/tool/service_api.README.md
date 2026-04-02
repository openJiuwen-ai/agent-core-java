# service_api

`com.openjiuwen.core.foundation.tool.service_api` contains REST adapter types that map structured tool inputs onto HTTP requests and normalized responses.

## Modules

| Module | Description |
| --- | --- |
| [`parser`](service_api/parser.README.md) | contains response parser and decompressor components used by REST-backed tools. |

## Core Types

| Type | Description |
| --- | --- |
| [`ApiParamLocation`](service_api/ApiParamLocation.md) | API parameter locations based on OpenAPI specification. |
| [`ApiParamMapper`](service_api/ApiParamMapper.md) | Maps input parameters to their corresponding API locations (query, path, body, header). Handles parameter distribution based on schema definitions and provides default value merging for query, path, and header parameters. |
| [`RestfulApi`](service_api/RestfulApi.md) | RESTful API tool that executes HTTP requests. Uses JDK `HttpClient` instead of aiohttp. |
| [`RestfulApiCard`](service_api/RestfulApiCard.md) | HTTP tool metadata that captures the target URL, HTTP method, default headers/query/path values, timeout, and response-size limit. |

## Notes

- `ApiParamMapperTest` covers location mapping and default-value merges.
- `RestfulApiTest` covers GET path/query expansion, response shaping, and `raise_for_status` handling.
