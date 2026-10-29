#!/usr/bin/env python3
"""Offline tests for gitcode_issue.py."""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import os
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

import gitcode_issue


VALID_TITLE = "[BUG] 空输入触发解析器异常"
VALID_BODY = """## 问题摘要

解析器收到空输入时抛出未处理异常。

## 复现环境

- 目标分支或版本：730
- JDK：17
- 操作系统：Linux
- 相关模块：parser

## 复现步骤

1. 创建空字符串输入。
2. 调用解析入口并观察结果。

## 实际结果

抛出未处理的 IllegalStateException。

## 预期结果

返回可识别的空输入校验错误。

## 证据

```text
IllegalStateException: empty input
```

## 代码定位

- 已确认存在的目标文件：由 Agent 根据仓库证据检索确定
- 相关类或符号：Parser
- 定位依据：根据异常类型检索调用链

## 验收标准

- [ ] 空输入返回稳定的校验错误。
- [ ] 非空输入的现有行为保持不变。
- [ ] 修改后通过 Java 编译 Gate；测试执行结果由人工或 CI 单独确认

## 变更边界

- 仅允许对 Java 主源码或测试源码进行最小修改。
- 不修改构建、CI、文档、示例、Skill、生成物或运行时文件。
- 不新增依赖，不降低安全校验，不访问外部服务。"""


class GitCodeIssueToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.repository = self.root / "repo"
        (self.repository / ".git").mkdir(parents=True)
        (self.repository / "pom.xml").write_text("<project/>", encoding="utf-8")
        self.identity = self.root / "git-identity.inc"
        self.identity.write_text(
            "[user]\n    name = Personal User\n    email = user@example.com\n",
            encoding="utf-8",
        )
        self.token = self.root / "gitcode-config.json"
        self.token.write_text(
            json.dumps({"gitCodeToken": "unit-test-only-not-a-real-pat"}) + "\n",
            encoding="utf-8",
        )
        if os.name != "nt":
            self.token.chmod(0o600)
        self.draft = self.root / "issue.md"
        self._write_draft(VALID_TITLE, VALID_BODY)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _write_draft(self, title: str, body: str) -> None:
        self.draft.write_text(
            "---\ntitle: "
            + json.dumps(title, ensure_ascii=False)
            + f"\n---\n\n{body}\n",
            encoding="utf-8",
        )

    def _submit_args(self, confirmed: bool = True) -> argparse.Namespace:
        return argparse.Namespace(
            repo="owner/repository",
            expected_login="personal-user",
            identity_file=str(self.identity),
            token_file=str(self.token),
            draft=str(self.draft),
            repository_root=str(self.repository),
            confirm_submit=confirmed,
        )

    def test_load_credentials_accepts_separated_personal_files(self) -> None:
        credentials = gitcode_issue.load_credentials(str(self.identity), str(self.token))
        self.assertEqual("Personal User", credentials.name)
        self.assertEqual("user@example.com", credentials.email)
        self.assertTrue(credentials.token.startswith("unit-test-only-"))

    @unittest.skipIf(os.name == "nt", "POSIX mode check is not available on Windows")
    def test_load_credentials_rejects_readable_token_file(self) -> None:
        self.token.chmod(0o644)
        with self.assertRaises(gitcode_issue.ToolError) as raised:
            gitcode_issue.load_credentials(str(self.identity), str(self.token))
        self.assertEqual("TOKEN_FILE_PERMISSIONS_UNSAFE", raised.exception.code)

    def test_validate_accepts_complete_draft_with_agent_search(self) -> None:
        draft = gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual(VALID_TITLE, draft.title)
        self.assertEqual((), draft.explicit_targets)
        self.assertEqual(64, len(draft.digest))

    def test_validate_checks_explicit_target_exists(self) -> None:
        target = self.repository / "src/main/java/demo/Parser.java"
        target.parent.mkdir(parents=True)
        target.write_text("package demo;\n", encoding="utf-8")
        body = VALID_BODY.replace(
            gitcode_issue.FALLBACK_LOCATION,
            "src/main/java/demo/Parser.java",
        )
        self._write_draft(VALID_TITLE, body)
        draft = gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual(("src/main/java/demo/Parser.java",), draft.explicit_targets)

        target.unlink()
        with self.assertRaises(gitcode_issue.ToolError) as raised:
            gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual("TARGET_PATH_NOT_FOUND", raised.exception.code)

    def test_validate_rejects_secret_in_body(self) -> None:
        body = VALID_BODY.replace(
            "IllegalStateException: empty input",
            "Authorization" + ": " + "Bearer " + "unit-test-only-secret-value",
        )
        self._write_draft(VALID_TITLE, body)
        with self.assertRaises(gitcode_issue.ToolError) as raised:
            gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual("BODY_SECRET_DETECTED", raised.exception.code)

    def test_validate_allows_build_output_path_as_evidence(self) -> None:
        body = VALID_BODY.replace(
            "IllegalStateException: empty input",
            "Failure report: target/surefire-reports/ParserTest.txt",
        )
        self._write_draft(VALID_TITLE, body)
        draft = gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual(VALID_TITLE, draft.title)

    def test_validate_rejects_out_of_scope_change_request(self) -> None:
        body = VALID_BODY.replace(
            "仅允许对 Java 主源码或测试源码进行最小修改。",
            "同时修改 resources/skills/parser/SKILL.md。",
        )
        self._write_draft(VALID_TITLE, body)
        with self.assertRaises(gitcode_issue.ToolError) as raised:
            gitcode_issue.load_draft(str(self.draft), str(self.repository))
        self.assertEqual("OUT_OF_SCOPE_PATH_FOUND", raised.exception.code)

    def test_submit_creates_without_bug_then_adds_bug(self) -> None:
        calls: list[tuple[str, object]] = []
        issue_node = {
            "number": 42,
            "title": VALID_TITLE,
            "body": VALID_BODY,
            "state": "opened",
            "html_url": "https://gitcode.com/owner/repository/issues/42",
            "labels": [],
            "user": {"login": "personal-user"},
        }

        class FakeApi:
            def __init__(self, token: str):
                self.token = token

            def get(self, path: str):
                calls.append(("GET", path))
                if path == "/user":
                    return {
                        "login": "personal-user",
                        "email": "user@example.com",
                    }
                if path.endswith("/labels?per_page=100"):
                    return [{"name": "bug"}]
                if "/issues?" in path:
                    return []
                if path == "/repos/owner/repository":
                    return {
                        "full_name": "owner/repository",
                        "has_issues": True,
                    }
                if path.endswith("/issues/42"):
                    return issue_node
                raise AssertionError(f"Unexpected GET {path}")

            def post_form(self, path: str, fields: dict[str, str]):
                calls.append(("POST_FORM", (path, fields)))
                self.assert_create_fields(fields)
                return issue_node

            @staticmethod
            def assert_create_fields(fields: dict[str, str]) -> None:
                if "labels" in fields:
                    raise AssertionError("Issue creation must not include labels")
                if fields["repo"] != "repository":
                    raise AssertionError("Wrong repository form field")

            def post_json(self, path: str, payload):
                calls.append(("POST_JSON", (path, payload)))
                if payload != ["bug"]:
                    raise AssertionError("Only the bug label may be added")
                issue_node["labels"] = [{"name": "bug"}]
                return issue_node["labels"]

        output = io.StringIO()
        with mock.patch.object(gitcode_issue, "GitCodeApi", FakeApi):
            with contextlib.redirect_stdout(output):
                gitcode_issue.command_submit(self._submit_args())

        rendered = output.getvalue()
        self.assertIn("ISSUE_NUMBER=42", rendered)
        self.assertIn("EVOLVER_TRIGGER=bug-label-added", rendered)
        self.assertNotIn("unit-test-only-not-a-real-pat", rendered)
        writes = [kind for kind, _ in calls if kind.startswith("POST")]
        self.assertEqual(["POST_FORM", "POST_JSON"], writes)

    def test_submit_requires_confirmation_before_network_access(self) -> None:
        with mock.patch.object(gitcode_issue, "GitCodeApi") as api:
            with self.assertRaises(gitcode_issue.ToolError) as raised:
                gitcode_issue.command_submit(self._submit_args(confirmed=False))
        self.assertEqual("SUBMISSION_NOT_CONFIRMED", raised.exception.code)
        api.assert_not_called()

    def test_api_classifies_os_error_subclasses_as_transport_failures(self) -> None:
        failures = (
            urllib.error.URLError("offline"),
            TimeoutError("timed out"),
            OSError("transport unavailable"),
        )
        for failure in failures:
            with self.subTest(failure=type(failure).__name__):
                api = gitcode_issue.GitCodeApi("unit-test-only-not-a-real-pat")
                with mock.patch.object(api._opener, "open", side_effect=failure):
                    with self.assertRaises(gitcode_issue.ApiError) as raised:
                        api.get("/user")
                self.assertFalse(raised.exception.uncertain)
                self.assertNotIn("unit-test-only-not-a-real-pat", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
