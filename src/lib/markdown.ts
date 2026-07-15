function isTableRow(line: string) {
  return line.split("|").length >= 3;
}

function isTableSeparator(line: string) {
  const cells = line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());

  return cells.length >= 2 && cells.every((cell) => /^:?-{3,}:?$/.test(cell));
}

function isLooseTableSeparator(line: string) {
  const trimmed = line.trim();
  return trimmed.includes("|") && /-{3}/.test(trimmed) && /^[\s|:-]+$/.test(trimmed);
}

function getTableColumnCount(line: string) {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|").length;
}

function buildTableSeparator(columnCount: number) {
  return `| ${Array.from({ length: columnCount }, () => "---").join(" | ")} |`;
}

function findNextContentLine(lines: string[], start: number) {
  for (let index = start; index < lines.length; index += 1) {
    if (lines[index].trim()) return index;
  }
  return -1;
}

/**
 * 修复大模型常见的非标准 GFM 表格：移除表头、分隔线及数据行之间的空行。
 * 代码围栏中的内容保持原样，避免修改代码示例的格式。
 */
export function normalizeMarkdownTables(content: string) {
  const lines = content.split("\n");
  const normalized: string[] = [];
  let fenceMarker: "```" | "~~~" | undefined;
  let insideTable = false;
  let tableColumnCount = 0;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const trimmed = line.trimStart();
    const currentFence = trimmed.startsWith("```")
      ? "```"
      : trimmed.startsWith("~~~")
        ? "~~~"
        : undefined;

    if (currentFence) {
      fenceMarker = fenceMarker === currentFence ? undefined : fenceMarker || currentFence;
      normalized.push(line);
      continue;
    }

    if (fenceMarker) {
      normalized.push(line);
      continue;
    }

    if (insideTable && !line.trim()) {
      const nextIndex = findNextContentLine(lines, index + 1);
      if (nextIndex >= 0 && isTableRow(lines[nextIndex])) continue;
      insideTable = false;
      tableColumnCount = 0;
    }

    if (!insideTable && isTableRow(line)) {
      const nextIndex = findNextContentLine(lines, index + 1);
      const nextLine = nextIndex >= 0 ? lines[nextIndex] : "";
      insideTable = isTableSeparator(nextLine) || isLooseTableSeparator(nextLine);
      tableColumnCount = insideTable ? getTableColumnCount(line) : 0;
    }

    if (insideTable && isLooseTableSeparator(line)) {
      normalized.push(buildTableSeparator(tableColumnCount));
      continue;
    }

    if (insideTable && line.trim() && !isTableRow(line)) {
      insideTable = false;
      tableColumnCount = 0;
    }
    normalized.push(line);
  }

  return normalized.join("\n");
}
