import re


def normalize_chapter_text(value):
    normalized = (value or "").replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ")
    normalized = re.sub(r"(?:[ \t\f]{2,}|\u3000{2,})", "\n\n", normalized)
    return "\n\n".join(line.strip() for line in re.split(r"\n+", normalized) if line.strip())
