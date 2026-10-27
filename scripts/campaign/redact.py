#!/usr/bin/env python3
"""Redact local excerpts before sharing; inspect the result before committing."""
import re
import sys

PATTERNS = [
    (r'(?i)\b(?:token|password|authorization|secret|api[_-]?key)\b\s*[:=]\s*\S+', '[REDACTED]'),
    (r'(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b', '[REDACTED]'),
    (r'\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b', '[REDACTED]'),
    (r'(?<!\w)(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?', '[REDACTED]'),
    (r'(?i)\bplayer\d+\b', '[REDACTED]'),
    (r'/home/[^\s\]\),]+', '[REDACTED]'),
    (r'(?i)\b(?:audio|sound)\s+device\b[^\n]*', '[REDACTED]'),
    (r'\b[A-Za-z0-9_-]{40,}\b', '[REDACTED]'),
]

def redact(text):
    for pattern, replacement in PATTERNS:
        text = re.sub(pattern, replacement, text)
    return text

if __name__ == '__main__':
    print(redact(sys.stdin.read()), end='')
