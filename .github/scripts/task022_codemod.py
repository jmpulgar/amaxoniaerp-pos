from pathlib import Path
import textwrap

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOTS = [
    ROOT / "amaxoniaerp-backend/src/main/kotlin",
    ROOT / "amaxoniaerp-backend/src/test/kotlin",
]
LIMIT = 120


def regular_string_spans(line: str) -> list[tuple[int, int]]:
    spans: list[tuple[int, int]] = []
    index = 0
    while index < len(line):
        if line.startswith("//", index):
            break
        if line.startswith('"""', index):
            end = line.find('"""', index + 3)
            if end < 0:
                return spans
            index = end + 3
            continue
        if line[index] == '"':
            start = index
            index += 1
            escaped = False
            while index < len(line):
                char = line[index]
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    spans.append((start, index))
                    index += 1
                    break
                index += 1
            continue
        if line[index] == "'":
            index += 1
            while index < len(line):
                if line[index] == "\\":
                    index += 2
                    continue
                if line[index] == "'":
                    index += 1
                    break
                index += 1
            continue
        index += 1
    return spans


def safe_spaces(content: str) -> list[int]:
    positions: list[int] = []
    interpolation_depth = 0
    index = 0
    while index < len(content):
        if content[index] == "\\":
            index += 2
            continue
        if content.startswith("${", index):
            interpolation_depth += 1
            index += 2
            continue
        if interpolation_depth and content[index] == "}":
            interpolation_depth -= 1
            index += 1
            continue
        if content[index] == " " and interpolation_depth == 0:
            positions.append(index)
        index += 1
    return positions


def split_regular_string_line(line: str) -> list[str]:
    if len(line) <= LIMIT or '"""' in line:
        return [line]
    candidates = []
    for start, end in regular_string_spans(line):
        content = line[start + 1 : end]
        spaces = safe_spaces(content)
        if spaces:
            candidates.append((end - start, start, end, content, spaces))
    if not candidates:
        return [line]
    _, start, end, content, spaces = max(candidates)
    desired = max(8, min(len(content) - 8, LIMIT - start - 5))
    split_at = min(spaces, key=lambda position: abs(position - desired))
    if split_at < 4 or len(content) - split_at < 4:
        return [line]
    left = content[: split_at + 1]
    right = content[split_at + 1 :]
    continuation = " " * (len(line) - len(line.lstrip()) + 4)
    first = line[: start + 1] + left + '" +'
    second = continuation + '"' + right + '"' + line[end + 1 :]
    result: list[str] = []
    for part in (first, second):
        if len(part) > LIMIT and part != line:
            result.extend(split_regular_string_line(part))
        else:
            result.append(part)
    return result


def wrap_comment(line: str) -> list[str]:
    if len(line) <= LIMIT:
        return [line]
    stripped = line.lstrip()
    indent = line[: len(line) - len(stripped)]
    prefix = next((candidate for candidate in (" * ", "// ", "/// ") if stripped.startswith(candidate)), None)
    if prefix is None:
        return [line]
    body = stripped[len(prefix) :]
    if " " not in body:
        return [line]
    width = max(20, LIMIT - len(indent) - len(prefix))
    return [
        indent + prefix + part
        for part in textwrap.wrap(body, width=width, break_long_words=False, break_on_hyphens=False)
    ]


def transform(text: str) -> str:
    output: list[str] = []
    for line in text.splitlines():
        for commented in wrap_comment(line):
            output.extend(split_regular_string_line(commented))
    return "\n".join(output) + "\n"


changed = 0
for source_root in SOURCE_ROOTS:
    for path in source_root.rglob("*.kt"):
        original = path.read_text(encoding="utf-8")
        updated = transform(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1

if changed == 0:
    raise RuntimeError("Expected at least one long-line formatting change")
