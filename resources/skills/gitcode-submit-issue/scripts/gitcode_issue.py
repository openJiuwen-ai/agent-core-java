#!/usr/bin/env python3
"""Validate and submit GitCode Issues that intentionally trigger Issue Evolver."""

from __future__ import annotations

import argparse
import configparser
import hashlib
import json
import os
import re
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Optional, Union


API_BASE = "https://api.gitcode.com/api/v5"
MAX_BODY_CHARS = 12_000
MAX_RESPONSE_BYTES = 1_048_576
FALLBACK_LOCATION = "由 Agent 根据仓库证据检索确定"
REQUIRED_HEADINGS = (
    "问题摘要",
    "复现环境",
    "复现步骤",
    "实际结果",
    "预期结果",
    "证据",
    "代码定位",
    "验收标准",
    "变更边界",
)
TARGET_PATH_PATTERN = re.compile(
    r"(?<![A-Za-z0-9._-])"
    r"(src/(?:main|test)/[A-Za-z0-9_./-]*\.java)"
)
EXCLUDED_PATH_PATTERN = re.compile(
    r"(?i)(?<![A-Za-z0-9._-])"
    r"(?:(?:documents|examples|resources|logs|target)/[^\s`'\"<>|),;:]*)"
)
PREFLIGHT_OUT_OF_SCOPE_PATTERN = re.compile(
    r"(?i)(?<![A-Za-z0-9._-])(?:pom\.xml|README[^/\s`'\"<>|),;:]*\.md)"
)
CHANGE_INTENT_PATTERN = re.compile(
    r"(?i)(?:\b(?:modify|update|edit|change|add|create|delete|remove|rename|write|implement|fix)\b"
    r"|修改|更新|编辑|新增|新建|删除|移除|编写|调整|修复)"
)
NEGATED_INTENT_PATTERN = re.compile(
    r"(?i)(?:\b(?:do\s+not|don't|must\s+not|should\s+not|never|without)\b"
    r"|不要|不得|不应|禁止|严禁|无需|不需要)"
)
PLACEHOLDER_PATTERN = re.compile(r"\{\{[^{}]*\}\}", re.DOTALL)
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(
        r"(?i)\b(?:authorization|private-token)\s*:\s*"
        r"(?:bearer\s+)?[A-Za-z0-9._~+/=-]{8,}"
    ),
    re.compile(
        r"(?i)\b(?:access_token|gitcodetoken|api[_-]?key)\s*[=:]\s*"
        r"[A-Za-z0-9._~+/=-]{8,}"
    ),
)
LOGIN_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")
REPO_SEGMENT_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")


class ToolError(Exception):
    """Expected, sanitized command failure."""

    def __init__(self, code: str, message: str, exit_code: int = 2):
        super().__init__(message)
        self.code = code
        self.exit_code = exit_code


class ApiError(ToolError):
    """Sanitized GitCode API failure."""

    def __init__(self, message: str, status: int = 0, uncertain: bool = False):
        code = "API_RESULT_UNCERTAIN" if uncertain else "API_REQUEST_FAILED"
        super().__init__(code, message, 4 if uncertain else 3)
        self.status = status
        self.uncertain = uncertain


class _RejectRedirects(urllib.request.HTTPRedirectHandler):
    """Keep the Authorization header pinned to the configured API host."""

    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


@dataclass(frozen=True)
class Credentials:
    """Validated local submitter credentials."""

    name: str
    email: str
    token: str
    identity_path: Path
    token_path: Path
    permission_status: str


@dataclass(frozen=True)
class Draft:
    """Validated Issue draft."""

    title: str
    body: str
    digest: str
    explicit_targets: tuple[str, ...]


@dataclass(frozen=True)
class RemoteContext:
    """Validated GitCode account and target repository."""

    login: str
    repository: str
    identity_email_match: str


@dataclass(frozen=True)
class IssueInfo:
    """Minimal safe Issue metadata."""

    number: int
    title: str
    body: str
    state: str
    url: str
    labels: tuple[str, ...]
    author_login: str


class GitCodeApi:
    """Small GitCode client that never places a PAT in a URL."""

    def __init__(self, token: str):
        self._token = token
        self._base_url = _official_api_base()
        self._opener = urllib.request.build_opener(_RejectRedirects())

    def get(self, path: str) -> Any:
        return self._request("GET", path)

    def post_json(self, path: str, payload: Any) -> Any:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        return self._request("POST", path, data, "application/json; charset=utf-8")

    def post_form(self, path: str, fields: dict[str, str]) -> Any:
        boundary = "gitcode-submit-issue-" + uuid.uuid4().hex
        data = _multipart(fields, boundary)
        return self._request("POST", path, data, f"multipart/form-data; boundary={boundary}")

    def _request(
        self,
        method: str,
        path: str,
        data: Optional[bytes] = None,
        content_type: Optional[str] = None,
    ) -> Any:
        if not path.startswith("/"):
            raise ToolError("INTERNAL_PATH_INVALID", "API path must start with '/'")
        headers = {
            "Accept": "application/json",
            "Authorization": "Bearer " + self._token,
            "User-Agent": "agent-core-java-gitcode-submit-issue/1",
        }
        if content_type is not None:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(
            self._base_url + path,
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with self._opener.open(request, timeout=30) as response:
                raw = response.read(MAX_RESPONSE_BYTES + 1)
                if len(raw) > MAX_RESPONSE_BYTES:
                    raise ApiError("GitCode response exceeded the safe size limit")
                if not raw.strip():
                    return None
                try:
                    return json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise ApiError("GitCode returned an invalid JSON response") from exc
        except urllib.error.HTTPError as exc:
            message = _http_error_message(exc).replace(self._token, "<redacted>")
            raise ApiError(
                f"GitCode returned HTTP {exc.code}: {message}",
                status=exc.code,
                uncertain=False,
            ) from None
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            uncertain = method != "GET"
            operation = "write" if uncertain else "read"
            raise ApiError(
                f"GitCode {operation} request failed at the transport layer",
                uncertain=uncertain,
            ) from exc


def _official_api_base() -> str:
    parsed = urllib.parse.urlsplit(API_BASE)
    if (
        parsed.scheme != "https"
        or parsed.hostname != "api.gitcode.com"
        or parsed.port not in (None, 443)
        or parsed.username is not None
        or parsed.password is not None
        or parsed.path.rstrip("/") != "/api/v5"
        or parsed.query
        or parsed.fragment
    ):
        raise ToolError(
            "API_BASE_INVALID",
            "GitCode API base must be the official https://api.gitcode.com/api/v5 endpoint",
        )
    return API_BASE.rstrip("/")


def _multipart(fields: dict[str, str], boundary: str) -> bytes:
    chunks: list[bytes] = []
    for name, value in fields.items():
        if any(character in name for character in "\r\n\""):
            raise ToolError("FORM_FIELD_INVALID", "Invalid multipart field name")
        chunks.extend(
            (
                f"--{boundary}\r\n".encode("ascii"),
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("ascii"),
                value.encode("utf-8"),
                b"\r\n",
            )
        )
    chunks.append(f"--{boundary}--\r\n".encode("ascii"))
    return b"".join(chunks)


def _http_error_message(error: urllib.error.HTTPError) -> str:
    try:
        raw = error.read(4096)
        parsed = json.loads(raw.decode("utf-8"))
        if isinstance(parsed, dict):
            value = parsed.get("message") or parsed.get("error")
            if isinstance(value, str) and value.strip():
                return _sanitize_message(value)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        pass
    return "request rejected"


def _sanitize_message(value: str) -> str:
    sanitized = re.sub(
        r"(?i)(authorization|private-token|access_token|gitcodetoken)"
        r"(\s*[:=]\s*)(\S+)",
        r"\1\2<redacted>",
        value.replace("\r", " ").replace("\n", " "),
    )
    return sanitized[:300]


def _plain_file(path_value: str, label: str) -> Path:
    path = Path(path_value).expanduser()
    if not path.exists():
        raise ToolError("CONFIG_FILE_MISSING", f"{label} file does not exist")
    if path.is_symlink():
        raise ToolError("CONFIG_SYMLINK_REJECTED", f"{label} file must not be a symbolic link")
    if not path.is_file():
        raise ToolError("CONFIG_FILE_INVALID", f"{label} path is not a regular file")
    return path.resolve()


def _load_identity(path_value: str) -> tuple[str, str, Path]:
    path = _plain_file(path_value, "Git identity")
    parser = configparser.ConfigParser(interpolation=None, strict=True)
    try:
        with path.open("r", encoding="utf-8") as stream:
            parser.read_file(stream)
    except (OSError, UnicodeDecodeError, configparser.Error) as exc:
        raise ToolError("IDENTITY_PARSE_FAILED", "Unable to parse git-identity.inc") from exc
    if parser.sections() != ["user"]:
        raise ToolError("IDENTITY_SCHEMA_INVALID", "git-identity.inc must contain only [user]")
    if set(parser["user"].keys()) != {"name", "email"}:
        raise ToolError(
            "IDENTITY_SCHEMA_INVALID",
            "git-identity.inc [user] must contain only name and email",
        )
    name = parser["user"]["name"].strip()
    email = parser["user"]["email"].strip()
    if _placeholder(name) or not name:
        raise ToolError("IDENTITY_NAME_INVALID", "Git identity name is missing or still a placeholder")
    if _placeholder(email) or not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        raise ToolError("IDENTITY_EMAIL_INVALID", "Git identity email is missing or invalid")
    return name, email, path


def _load_token(path_value: str) -> tuple[str, Path, str]:
    path = _plain_file(path_value, "GitCode token")
    try:
        with path.open("r", encoding="utf-8") as stream:
            parsed = json.load(stream)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ToolError("TOKEN_PARSE_FAILED", "Unable to parse gitcode-config.json") from exc
    if not isinstance(parsed, dict) or set(parsed) != {"gitCodeToken"}:
        raise ToolError(
            "TOKEN_SCHEMA_INVALID",
            "gitcode-config.json must contain only gitCodeToken; do not mix Bot secrets",
        )
    token = parsed.get("gitCodeToken")
    if not isinstance(token, str) or _placeholder(token) or len(token.strip()) < 16:
        raise ToolError("TOKEN_VALUE_INVALID", "GitCode token is missing or still a placeholder")
    if token != token.strip() or "\r" in token or "\n" in token:
        raise ToolError("TOKEN_VALUE_INVALID", "GitCode token contains surrounding whitespace")
    permission_status = "not-checked-on-windows"
    if os.name != "nt":
        mode = stat.S_IMODE(path.stat().st_mode)
        if mode & 0o077:
            raise ToolError(
                "TOKEN_FILE_PERMISSIONS_UNSAFE",
                f"gitcode-config.json mode is {mode:04o}; set it to 0600",
            )
        permission_status = "0600-or-stricter"
    return token, path, permission_status


def load_credentials(identity_file: str, token_file: str) -> Credentials:
    name, email, identity_path = _load_identity(identity_file)
    token, token_path, permission_status = _load_token(token_file)
    return Credentials(
        name=name,
        email=email,
        token=token,
        identity_path=identity_path,
        token_path=token_path,
        permission_status=permission_status,
    )


def _placeholder(value: str) -> bool:
    upper = value.strip().upper()
    return (
        not upper
        or "{{" in value
        or upper.startswith("YOUR_")
        or upper.startswith("REPLACE_WITH")
        or upper in {"TODO", "TBD", "待填写"}
    )


def _parse_frontmatter(text: str) -> tuple[str, str]:
    normalized = text.replace("\r\n", "\n")
    if not normalized.startswith("---\n"):
        raise ToolError("DRAFT_FRONTMATTER_MISSING", "Draft must start with title frontmatter")
    end = normalized.find("\n---\n", 4)
    if end < 0:
        raise ToolError("DRAFT_FRONTMATTER_INVALID", "Draft frontmatter is not closed")
    frontmatter = normalized[4:end]
    body = normalized[end + 5 :].strip()
    values: dict[str, str] = {}
    for line in frontmatter.splitlines():
        if not line.strip():
            continue
        if ":" not in line:
            raise ToolError("DRAFT_FRONTMATTER_INVALID", "Invalid draft frontmatter entry")
        key, raw_value = line.split(":", 1)
        key = key.strip()
        if key in values:
            raise ToolError("DRAFT_FRONTMATTER_INVALID", "Duplicate draft frontmatter key")
        if key != "title":
            raise ToolError("DRAFT_FRONTMATTER_INVALID", "Draft frontmatter may contain only title")
        try:
            value = json.loads(raw_value.strip())
        except json.JSONDecodeError as exc:
            raise ToolError(
                "DRAFT_FRONTMATTER_INVALID",
                "Draft title must be a JSON-compatible double-quoted string",
            ) from exc
        if not isinstance(value, str):
            raise ToolError("DRAFT_FRONTMATTER_INVALID", "Draft title must be a string")
        values[key] = value
    if set(values) != {"title"}:
        raise ToolError("DRAFT_FRONTMATTER_INVALID", "Draft frontmatter requires title")
    return values["title"], body


def load_draft(path_value: str, repository_root: str) -> Draft:
    path = _plain_file(path_value, "Issue draft")
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise ToolError("DRAFT_READ_FAILED", "Unable to read Issue draft as UTF-8") from exc
    title, body = _parse_frontmatter(text)
    return validate_issue(title, body, repository_root)


def validate_issue(title: str, body: str, repository_root: str) -> Draft:
    if "\r" in title or "\n" in title:
        raise ToolError("TITLE_INVALID", "Issue title must be a single line")
    if not title.startswith("[BUG] "):
        raise ToolError("TITLE_INVALID", "Issue title must start with '[BUG] '")
    if len(title) > 200 or len(title.strip()) < 12 or PLACEHOLDER_PATTERN.search(title):
        raise ToolError("TITLE_INVALID", "Issue title is incomplete or exceeds 200 characters")
    if not body:
        raise ToolError("BODY_INVALID", "Issue body is empty")
    if len(body) > MAX_BODY_CHARS:
        raise ToolError(
            "BODY_TOO_LONG",
            f"Issue body has {len(body)} characters; maximum is {MAX_BODY_CHARS}",
        )
    if PLACEHOLDER_PATTERN.search(body) or "<!--" in body or "REPLACE_WITH" in body:
        raise ToolError("BODY_PLACEHOLDER_FOUND", "Issue body still contains a template placeholder")
    for pattern in SECRET_PATTERNS:
        if pattern.search(body):
            raise ToolError("BODY_SECRET_DETECTED", "Issue body appears to contain a credential")
    sections = _sections(body)
    for heading in REQUIRED_HEADINGS:
        if heading not in sections or not sections[heading].strip():
            raise ToolError("BODY_SECTION_MISSING", f"Issue body section '{heading}' is missing or empty")
    positions = [body.index(f"## {heading}") for heading in REQUIRED_HEADINGS]
    if positions != sorted(positions):
        raise ToolError("BODY_SECTION_ORDER_INVALID", "Issue body sections are out of order")
    if len(re.findall(r"(?m)^\s*\d+\.\s+\S", sections["复现步骤"])) < 2:
        raise ToolError("REPRO_STEPS_INCOMPLETE", "Issue requires at least two reproduction steps")
    if len(re.findall(r"(?m)^\s*-\s+\[ \]\s+\S", sections["验收标准"])) < 2:
        raise ToolError("ACCEPTANCE_INCOMPLETE", "Issue requires at least two unchecked acceptance items")
    if _requests_out_of_scope(body, sections["代码定位"]):
        raise ToolError(
            "OUT_OF_SCOPE_PATH_FOUND",
            "Issue requests or targets a path outside the Evolver Java source scope",
        )
    root = _repository_root(repository_root)
    targets = _explicit_targets(body)
    if not targets and FALLBACK_LOCATION not in sections["代码定位"]:
        raise ToolError(
            "CODE_LOCATION_INCOMPLETE",
            f"Provide an existing Java target or use '{FALLBACK_LOCATION}'",
        )
    for target in targets:
        if target.startswith("src/main/java/examples/"):
            raise ToolError(
                "OUT_OF_SCOPE_PATH_FOUND",
                f"Example source is excluded from the Evolver change scope: {target}",
            )
        candidate = root.joinpath(*PurePosixPath(target).parts)
        if candidate.is_symlink() or not candidate.is_file():
            raise ToolError(
                "TARGET_PATH_NOT_FOUND",
                f"Explicit target does not exist as a regular file: {target}",
            )
    digest = hashlib.sha256((title + "\0" + body).encode("utf-8")).hexdigest()
    return Draft(title=title, body=body, digest=digest, explicit_targets=targets)


def _requests_out_of_scope(body: str, code_location: str) -> bool:
    if EXCLUDED_PATH_PATTERN.search(code_location):
        return True
    for line in body.replace("\\", "/").splitlines():
        for pattern in (EXCLUDED_PATH_PATTERN, PREFLIGHT_OUT_OF_SCOPE_PATTERN):
            for match in pattern.finditer(line):
                start = max(0, match.start() - 96)
                end = min(len(line), match.end() + 96)
                context = line[start:end]
                if pattern is PREFLIGHT_OUT_OF_SCOPE_PATTERN:
                    if not NEGATED_INTENT_PATTERN.search(context):
                        return True
                elif (
                    CHANGE_INTENT_PATTERN.search(context)
                    and not NEGATED_INTENT_PATTERN.search(context)
                ):
                    return True
    return False


def _sections(body: str) -> dict[str, str]:
    matches = list(re.finditer(r"(?m)^## ([^\r\n]+)\s*$", body))
    sections: dict[str, str] = {}
    for index, match in enumerate(matches):
        heading = match.group(1).strip()
        if heading in sections:
            raise ToolError("BODY_SECTION_DUPLICATE", f"Duplicate Issue body section: {heading}")
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        sections[heading] = body[match.end() : end].strip()
    return sections


def _repository_root(path_value: str) -> Path:
    root = Path(path_value).expanduser().resolve()
    if not root.is_dir() or not (root / ".git").exists() or not (root / "pom.xml").is_file():
        raise ToolError(
            "REPOSITORY_ROOT_INVALID",
            "Repository root must contain .git and pom.xml",
        )
    return root


def _explicit_targets(body: str) -> tuple[str, ...]:
    targets: list[str] = []
    for match in TARGET_PATH_PATTERN.finditer(body.replace("\\", "/")):
        value = match.group(1)
        pure = PurePosixPath(value)
        if pure.is_absolute() or "." in pure.parts or ".." in pure.parts:
            raise ToolError("TARGET_PATH_INVALID", f"Unsafe explicit target path: {value}")
        normalized = pure.as_posix()
        if normalized not in targets:
            targets.append(normalized)
    return tuple(targets)


def _outside_repository(path: Path, repository_root: str, label: str) -> None:
    root = _repository_root(repository_root)
    try:
        common = Path(os.path.commonpath((str(path), str(root))))
    except ValueError:
        return
    if common == root:
        raise ToolError(
            "CREDENTIAL_INSIDE_REPOSITORY",
            f"{label} file must be stored outside the target repository",
        )


def _repo(value: str) -> tuple[str, str, str]:
    parts = value.split("/")
    if (
        len(parts) != 2
        or not REPO_SEGMENT_PATTERN.fullmatch(parts[0])
        or not REPO_SEGMENT_PATTERN.fullmatch(parts[1])
    ):
        raise ToolError("REPOSITORY_INVALID", "Repository must be an owner/repo path")
    return parts[0], parts[1], value


def _login(value: str) -> str:
    if not LOGIN_PATTERN.fullmatch(value):
        raise ToolError("LOGIN_INVALID", "Expected login has an invalid format")
    return value


def _quoted(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def verify_remote(
    api: GitCodeApi,
    repository: str,
    expected_login: str,
    identity_email: str,
) -> RemoteContext:
    owner, repo_name, canonical = _repo(repository)
    expected = _login(expected_login)
    profile = api.get("/user")
    if not isinstance(profile, dict):
        raise ApiError("GitCode user response was not an object")
    actual_login = str(profile.get("login") or "").strip()
    if not LOGIN_PATTERN.fullmatch(actual_login):
        raise ApiError("GitCode user response did not include a valid login")
    if actual_login.casefold() != expected.casefold():
        raise ToolError(
            "AUTHENTICATED_ACCOUNT_MISMATCH",
            f"PAT belongs to '{actual_login}', not expected personal login '{expected}'",
        )
    repository_node = api.get(f"/repos/{_quoted(owner)}/{_quoted(repo_name)}")
    if not isinstance(repository_node, dict):
        raise ApiError("GitCode repository response was not an object")
    if repository_node.get("has_issues") is False:
        raise ToolError("ISSUES_DISABLED", "Target repository has Issues disabled")
    labels_node = api.get(
        f"/repos/{_quoted(owner)}/{_quoted(repo_name)}/labels?per_page=100"
    )
    label_names = {_label_name(node) for node in _as_list(labels_node)}
    if "bug" not in label_names:
        raise ToolError(
            "BUG_LABEL_MISSING",
            "Target repository does not contain the exact lowercase 'bug' label",
        )
    profile_email = str(profile.get("email") or "").strip()
    if not profile_email:
        email_match = "not-exposed"
    elif profile_email.casefold() == identity_email.casefold():
        email_match = "true"
    else:
        email_match = "false-nonblocking"
    full_name = str(repository_node.get("full_name") or canonical).replace(" ", "")
    if full_name.casefold() != canonical.casefold():
        full_name = canonical
    return RemoteContext(
        login=actual_login,
        repository=full_name,
        identity_email_match=email_match,
    )


def _as_list(value: Any) -> list[Any]:
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("data", "items", "list"):
            nested = value.get(key)
            if isinstance(nested, list):
                return nested
    return []


def _label_name(value: Any) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        return str(value.get("name") or value.get("title") or "")
    return ""


def _issue_info(value: Any, repository: str) -> IssueInfo:
    if not isinstance(value, dict):
        raise ApiError("GitCode Issue response was not an object")
    raw_number = value.get("number", value.get("iid", -1))
    try:
        number = int(raw_number)
    except (TypeError, ValueError) as exc:
        raise ApiError("GitCode Issue response did not include a valid number") from exc
    if number <= 0:
        raise ApiError("GitCode Issue response did not include a valid number")
    url = _safe_issue_url(
        str(value.get("html_url") or value.get("web_url") or "").strip(),
        repository,
        number,
    )
    labels = tuple(
        name for name in (_label_name(node) for node in _as_list(value.get("labels"))) if name
    )
    user = value.get("user")
    author = str(user.get("login") or "") if isinstance(user, dict) else ""
    return IssueInfo(
        number=number,
        title=str(value.get("title") or ""),
        body=str(value.get("body") or value.get("description") or ""),
        state=str(value.get("state") or ""),
        url=url,
        labels=labels,
        author_login=author,
    )


def _safe_issue_url(value: str, repository: str, number: int) -> str:
    fallback = f"https://gitcode.com/{repository}/issues/{number}"
    if not value:
        return fallback
    parsed = urllib.parse.urlsplit(value)
    expected_path = f"/{repository}/issues/{number}"
    if (
        parsed.scheme == "https"
        and parsed.hostname == "gitcode.com"
        and parsed.port in (None, 443)
        and parsed.username is None
        and parsed.password is None
        and parsed.path.rstrip("/") == expected_path
        and not parsed.query
        and not parsed.fragment
    ):
        return value
    return fallback


def _get_issue(api: GitCodeApi, repository: str, number: int) -> IssueInfo:
    owner, repo_name, canonical = _repo(repository)
    node = api.get(
        f"/repos/{_quoted(owner)}/{_quoted(repo_name)}/issues/{number}"
    )
    return _issue_info(node, canonical)


def _find_matching_open_issues(
    api: GitCodeApi,
    repository: str,
    draft: Draft,
) -> list[IssueInfo]:
    owner, repo_name, canonical = _repo(repository)
    query = urllib.parse.urlencode(
        {
            "state": "open",
            "sort": "created_at",
            "direction": "desc",
            "page": "1",
            "per_page": "100",
        }
    )
    nodes = api.get(
        f"/repos/{_quoted(owner)}/{_quoted(repo_name)}/issues?{query}"
    )
    matches: list[IssueInfo] = []
    for node in _as_list(nodes):
        issue = _issue_info(node, canonical)
        if issue.title == draft.title and issue.body == draft.body:
            matches.append(issue)
    return matches


def _create_issue(api: GitCodeApi, repository: str, draft: Draft) -> IssueInfo:
    owner, repo_name, canonical = _repo(repository)
    node = api.post_form(
        f"/repos/{_quoted(owner)}/issues",
        {
            "repo": repo_name,
            "title": draft.title,
            "body": draft.body,
        },
    )
    return _issue_info(node, canonical)


def _add_bug_label(api: GitCodeApi, repository: str, number: int) -> None:
    owner, repo_name, _ = _repo(repository)
    api.post_json(
        f"/repos/{_quoted(owner)}/{_quoted(repo_name)}/issues/{number}/labels",
        ["bug"],
    )


def _is_open(issue: IssueInfo) -> bool:
    return issue.state.casefold() in {"open", "opened"}


def _emit(key: str, value: Union[str, int]) -> None:
    safe = str(value).replace("\r", " ").replace("\n", " ")
    print(f"{key}={safe}")


def command_check_config(args: argparse.Namespace) -> None:
    credentials = load_credentials(args.identity_file, args.token_file)
    _emit("CONFIG_STATUS", "valid")
    _emit("GIT_IDENTITY", "valid")
    _emit("GITCODE_TOKEN", "<redacted>")
    _emit("TOKEN_FILE_PERMISSIONS", credentials.permission_status)


def command_validate(args: argparse.Namespace) -> None:
    draft = load_draft(args.draft, args.repository_root)
    _emit("DRAFT_STATUS", "valid")
    _emit("TITLE", draft.title)
    _emit("BODY_CHARACTERS", len(draft.body))
    _emit("DRAFT_SHA256", draft.digest)
    _emit(
        "EXPLICIT_TARGETS",
        ",".join(draft.explicit_targets) if draft.explicit_targets else "agent-search",
    )


def _credentials_for_repository(args: argparse.Namespace) -> Credentials:
    credentials = load_credentials(args.identity_file, args.token_file)
    _outside_repository(credentials.identity_path, args.repository_root, "Git identity")
    _outside_repository(credentials.token_path, args.repository_root, "GitCode token")
    return credentials


def command_verify(args: argparse.Namespace) -> None:
    credentials = load_credentials(args.identity_file, args.token_file)
    context = verify_remote(
        GitCodeApi(credentials.token),
        args.repo,
        args.expected_login,
        credentials.email,
    )
    _emit("REMOTE_STATUS", "valid")
    _emit("AUTHENTICATED_LOGIN", context.login)
    _emit("TARGET_REPOSITORY", context.repository)
    _emit("BUG_LABEL", "available")
    _emit("IDENTITY_EMAIL_MATCH", context.identity_email_match)
    _emit("TOKEN_FILE_PERMISSIONS", credentials.permission_status)


def command_submit(args: argparse.Namespace) -> None:
    if not args.confirm_submit:
        raise ToolError(
            "SUBMISSION_NOT_CONFIRMED",
            "Remote creation requires --confirm-submit after explicit user approval",
        )
    draft = load_draft(args.draft, args.repository_root)
    credentials = _credentials_for_repository(args)
    api = GitCodeApi(credentials.token)
    context = verify_remote(api, args.repo, args.expected_login, credentials.email)
    matches = _find_matching_open_issues(api, args.repo, draft)
    if matches:
        issue = matches[0]
        _emit("SUBMISSION_STATUS", "existing-open-issue")
        _emit("ISSUE_NUMBER", issue.number)
        _emit("ISSUE_URL", issue.url)
        _emit("BUG_LABEL_PRESENT", str("bug" in issue.labels).lower())
        raise ToolError(
            "DUPLICATE_ISSUE_FOUND",
            "A matching open Issue already exists; do not create a duplicate",
            5,
        )
    reconciled_create = False
    try:
        issue = _create_issue(api, args.repo, draft)
    except ApiError as error:
        if not error.uncertain:
            raise
        try:
            matches = _find_matching_open_issues(api, args.repo, draft)
        except ToolError:
            matches = []
        if len(matches) != 1:
            raise ToolError(
                "CREATE_RESULT_UNCERTAIN",
                "Issue creation result is uncertain; search GitCode before any retry",
                4,
            ) from error
        issue = matches[0]
        reconciled_create = True
    _emit("ISSUE_CREATED", "true")
    _emit("ISSUE_NUMBER", issue.number)
    _emit("ISSUE_URL", issue.url)
    _emit("AUTHENTICATED_LOGIN", context.login)
    if not _is_open(issue):
        raise ToolError(
            "CREATED_ISSUE_NOT_OPEN",
            "Issue was created but is not open; bug label was not added",
            4,
        )
    if "bug" in issue.labels:
        raise ToolError(
            "BUG_LABEL_PRESENT_AT_CREATION",
            "Issue already had bug at creation; no qualifying label update was made",
            4,
        )
    trigger_status = _add_label_with_reconciliation(api, args.repo, issue.number)
    _emit("SUBMISSION_STATUS", "reconciled-created" if reconciled_create else "created")
    _emit("EVOLVER_TRIGGER", trigger_status)


def _add_label_with_reconciliation(
    api: GitCodeApi,
    repository: str,
    number: int,
) -> str:
    try:
        _add_bug_label(api, repository, number)
        return "bug-label-added"
    except ApiError as error:
        try:
            current = _get_issue(api, repository, number)
        except ToolError:
            raise ToolError(
                "BUG_LABEL_RESULT_UNCERTAIN",
                "Issue exists but bug-label update is uncertain; inspect it before retrying",
                4,
            ) from error
        if "bug" in current.labels:
            return "bug-label-confirmed-after-uncertain-response"
        raise ToolError(
            "BUG_LABEL_NOT_ADDED",
            "Issue exists but bug label was not added; do not rerun submit",
            4,
        ) from error


def command_trigger(args: argparse.Namespace) -> None:
    if not args.confirm_trigger:
        raise ToolError(
            "TRIGGER_NOT_CONFIRMED",
            "Remote label update requires --confirm-trigger after explicit user approval",
        )
    if args.issue_number <= 0:
        raise ToolError("ISSUE_NUMBER_INVALID", "Issue number must be positive")
    credentials = _credentials_for_repository(args)
    api = GitCodeApi(credentials.token)
    context = verify_remote(api, args.repo, args.expected_login, credentials.email)
    issue = _get_issue(api, args.repo, args.issue_number)
    if not _is_open(issue):
        raise ToolError("ISSUE_NOT_OPEN", "Issue must be open before adding the trigger label")
    if issue.author_login and issue.author_login.casefold() != context.login.casefold():
        raise ToolError(
            "ISSUE_AUTHOR_MISMATCH",
            "Authenticated personal account is not the Issue author",
        )
    validate_issue(issue.title, issue.body, args.repository_root)
    if "bug" in issue.labels:
        raise ToolError(
            "BUG_LABEL_ALREADY_PRESENT",
            "Issue already has bug; do not remove and re-add it automatically",
        )
    trigger_status = _add_label_with_reconciliation(api, args.repo, issue.number)
    _emit("TRIGGER_STATUS", "updated")
    _emit("ISSUE_NUMBER", issue.number)
    _emit("ISSUE_URL", issue.url)
    _emit("AUTHENTICATED_LOGIN", context.login)
    _emit("EVOLVER_TRIGGER", trigger_status)


def _add_credential_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--identity-file", required=True, help="Personal git-identity.inc path")
    parser.add_argument("--token-file", required=True, help="Personal gitcode-config.json path")


def _add_remote_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--repo", required=True, help="Exact target owner/repo")
    parser.add_argument(
        "--expected-login",
        required=True,
        help="Personal GitCode login expected from the PAT",
    )
    _add_credential_arguments(parser)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate and submit Evolver-ready GitCode bug Issues",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    check = subparsers.add_parser("check-config", help="Validate local files without network access")
    _add_credential_arguments(check)
    check.set_defaults(handler=command_check_config)

    validate = subparsers.add_parser("validate", help="Validate an Issue draft offline")
    validate.add_argument("--draft", required=True, help="Markdown draft path")
    validate.add_argument("--repository-root", required=True, help="Local target repository root")
    validate.set_defaults(handler=command_validate)

    verify = subparsers.add_parser("verify", help="Verify PAT identity, repository, and bug label")
    _add_remote_arguments(verify)
    verify.set_defaults(handler=command_verify)

    submit = subparsers.add_parser("submit", help="Create the Issue and then add the bug label")
    _add_remote_arguments(submit)
    submit.add_argument("--draft", required=True, help="Validated Markdown draft path")
    submit.add_argument("--repository-root", required=True, help="Local target repository root")
    submit.add_argument(
        "--confirm-submit",
        action="store_true",
        help="Confirm the two remote writes after explicit user approval",
    )
    submit.set_defaults(handler=command_submit)

    trigger = subparsers.add_parser("trigger", help="Recover an existing unlabelled Issue")
    _add_remote_arguments(trigger)
    trigger.add_argument("--issue-number", required=True, type=int, help="Existing Issue number")
    trigger.add_argument("--repository-root", required=True, help="Local target repository root")
    trigger.add_argument(
        "--confirm-trigger",
        action="store_true",
        help="Confirm the remote label update after explicit user approval",
    )
    trigger.set_defaults(handler=command_trigger)
    return parser


def main(argv: Optional[list[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        args.handler(args)
        return 0
    except ToolError as error:
        print(f"ERROR_CODE={error.code}", file=sys.stderr)
        print(f"ERROR={_sanitize_message(str(error))}", file=sys.stderr)
        return error.exit_code


if __name__ == "__main__":
    sys.exit(main())
