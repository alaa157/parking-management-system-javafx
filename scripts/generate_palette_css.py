#!/usr/bin/env python3
"""Generate native JavaFX CSS from the Java palette constants."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA_FILES = [
    ROOT / "src/main/java/com/parking/gui/DesignTokens.java",
    ROOT / "src/main/java/com/parking/gui/LightDesignTokens.java",
]
CSS = ROOT / "src/main/resources/parkingos.css"
TEMPLATE = ROOT / "src/main/resources/parkingos.css.template"
TOKENS = ROOT / "src/main/resources/parkingos-tokens.css"
PRINT_CSS = ROOT / "src/main/resources/parkingos-print.css"
PRINT_TEMPLATE = ROOT / "src/main/resources/parkingos-print.css.template"
HEADER = "/* AUTO-GENERATED FROM DesignTokens.java — DO NOT EDIT BY HAND. Run `mvn generate-resources` to regenerate. */\n"


def constants():
    result = {}
    pattern = re.compile(r'public static final String (\w+) = "([^"]+)";')
    for source in JAVA_FILES:
        for name, value in pattern.findall(source.read_text()):
            result[name] = value
    return result


def numeric_constants():
    """Phase 2: double/int constants (spacing, radius, type) for the tokens file."""
    result = {}
    pattern = re.compile(r'public static final (?:double|int) (\w+) = ([\d.]+);')
    for source in JAVA_FILES:
        for name, value in pattern.findall(source.read_text()):
            if name.startswith(("SPACE_", "RADIUS_", "TYPE_", "LINE_HEIGHT_", "LETTER_SPACING_")):
                result[name] = value
    return result


def kebab(name):
    return re.sub(r'([a-z0-9])([A-Z])', r'\1-\2', name).replace('_', '-').lower()


def normalize_light_aliases(source):
    """Use light semantic names only inside .theme-light CSS rules."""
    lines = source.splitlines(keepends=True)
    result = []
    active = False
    pending_light_selector = False
    depth = 0
    for line in lines:
        if depth == 0:
            pending_light_selector = ".theme-light" in line
            active = pending_light_selector
        if active:
            line = line.replace("@WHITE@", "@LIGHT_CARD@")
        else:
            line = line.replace("@LIGHT_CARD@", "@WHITE@")
        result.append(line)
        depth += line.count("{") - line.count("}")
        if depth <= 0:
            depth = 0
            active = False
            pending_light_selector = False
    return "".join(result)


def initialize_template(values):
    if TEMPLATE.exists():
        existing = TEMPLATE.read_text()
        normalized = re.sub(r'@@([A-Z][A-Z0-9_]*)@@', r'@\1@', existing)
        normalized = normalized.replace("@USER_LIGHT_TEXT@", "@LIGHT_TEXT@")
        normalized = normalized.replace("@USER_LIGHT_MUTED@", "@LIGHT_MUTED@")
        normalized = normalize_light_aliases(normalized)
        if normalized != existing:
            TEMPLATE.write_text(normalized)
        if "@" in normalized:
            return
    source = CSS.read_text()
    source = re.sub(r'^/\* AUTO-GENERATED.*?\*/\s*', '', source, count=1, flags=re.DOTALL)
    source = re.sub(r'(?<![\w-])white(?![\w-])', "@WHITE@", source, flags=re.IGNORECASE)
    source = re.sub(r'(?<![\w-])black(?![\w-])', "@BLACK@", source, flags=re.IGNORECASE)
    for name, value in sorted(values.items(), key=lambda item: len(item[1]), reverse=True):
        source = source.replace(value, f"@{name}@")
    source = normalize_light_aliases(source)
    TEMPLATE.write_text(source)


def generate(values):
    initialize_template(values)
    css = TEMPLATE.read_text()
    for name, value in values.items():
        css = css.replace(f"@{name}@", value)
    CSS.write_text(HEADER + css)

    print_css = PRINT_TEMPLATE.read_text()
    for name, value in values.items():
        print_css = print_css.replace(f"@{name}@", value)
    PRINT_CSS.write_text(HEADER + print_css)

    lines = [HEADER, ":root {"]
    for name, value in values.items():
        lines.append(f"    --fx-color-{kebab(name)}: {value};")
    for name, value in numeric_constants().items():
        unit = "px" if name.startswith(("SPACE_", "RADIUS_", "TYPE_")) else ""
        lines.append(f"    --fx-{kebab(name)}: {value}{unit};")
    lines.extend([
        "    --fx-font-family: \"Segoe UI\", \"Arial\";",
        "    --fx-font-weight-regular: 400;", "    --fx-font-weight-medium: 600;",
        "    --fx-font-weight-bold: 700;",
        "    --fx-duration-fast: 150ms;", "    --fx-duration-normal: 250ms;",
        "    --fx-duration-slow: 350ms;", "}", "",
    ])
    TOKENS.write_text("\n".join(lines))


if __name__ == "__main__":
    generate(constants())
