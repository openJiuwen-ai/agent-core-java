# com.openjiuwen.agent_evolving.sharing

`com.openjiuwen.agent_evolving.sharing` provides the Java package facade for Python `openjiuwen.agent_evolving.sharing`.

## class SharingPackage

`SharingPackage` mirrors Python's `openjiuwen/agent_evolving/sharing/__init__.py`.

- `PYTHON_MODULE`: `openjiuwen/agent_evolving/sharing/__init__.py`
- `DESCRIPTION`: `Experience sharing module.`
- `EXPORTED_SYMBOLS`: the exported symbol list in the same order as Python `__all__`

Current exported symbols:

- `SharingBackend`
- `LocalFileBackend`
- `KeywordExtractor`
- `QUERY_KEYWORDS_LLM_POLICY`
- `ShareStager`
- `ExperienceSharer`
- `SkillSharingContextProvider`
- `ensure_skill_id_in_content`
- `pack_skill_directory`
- `read_skill_id_from_content`
- `unpack_skill_package`
- `StagingResult`
- `QueryKeywords`
- `SharedExperience`
- `SharedSkillBundle`
- `SharingMeta`
- `SkillPackageMeta`
- `SkillSearchResult`
- `UploadResult`

Concrete classes and functions are implemented by the Java files mapped from their own Python modules. `SharingPackage` only records the package-level export contract.

## class KeywordExtractor

`KeywordExtractor` mirrors Python's `openjiuwen/agent_evolving/sharing/keyword_extractor.py`.

Core behavior:

- `parseFromOptimizerOutput` reads `keywords` and `summary` from either an `EvolutionPatch` or a JSON-like map.
- `extractQueryKeywords` returns empty data for blank excerpts.
- When no LLM/model is bound, query extraction returns empty keywords, the first 40 characters as intent, and preserves the raw excerpt.
- When an LLM is bound, it builds the language-specific keyword prompt, calls `LlmResilience.invokeTextWithRetry`, extracts the first JSON object from raw model output, trims non-empty keywords, caps keywords at 20, and caps intent at 80 characters.
- On framework or unexpected LLM failure, it returns the same non-blocking fallback as Python.

`QUERY_KEYWORDS_LLM_POLICY` mirrors the Python policy: 1500-second attempt timeout, 4000-second total budget, and 5 attempts.

## class ShareStager

`ShareStager` mirrors Python's `openjiuwen/agent_evolving/sharing/share_stager.py`.

Core behavior:

- `screenAndStage` never writes local evolution persistence and never uploads; it only screens records, wraps passing records, and calls `ExperienceSharer.stageForUpload`.
- Empty record lists return `StagingResult.empty()`.
- Execution-failure records are dropped only when messages contain no successful `tool` or `function` result.
- Score QC drops records below `qcScoreThreshold` with the same formatted reason as Python.
- Passing records are wrapped as `SharedExperience` with a copied `EvolutionRecord`, keywords and summary parsed by `KeywordExtractor`, and `SharingMeta` populated from skill name, skill version, source user id, and score.
- Duplicate or hub-side acceptance rules remain owned by `ExperienceSharer` and the backend.
