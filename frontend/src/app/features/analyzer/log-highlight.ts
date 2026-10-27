/**
 * Lightweight syntax highlighting for raw log lines. Produces text tokens with a CSS class; the template renders them
 * through text interpolation, so log content is never interpreted as HTML.
 */
export interface Token {
  text: string;
  cls: string;
}

const TS = /^\[?\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d{1,9})?(?:\s?(?:Z|[+-]\d{2}:?\d{2}))?\]?/;
const TEXT_RULES: [RegExp, string][] = [
  [/^\b(?:ERROR|FATAL|SEVERE)\b/, 'lv-error'],
  [/^\b(?:WARN|WARNING)\b/, 'lv-warn'],
  [/^\b(?:INFO)\b/, 'lv-info'],
  [/^\b(?:DEBUG|TRACE)\b/, 'lv-debug'],
  [/^\[[^\]]*\]/, 'bracket'],
  [/^\[MASKED[^\]]*\]/, 'masked'],
  [/^[\w.-]+(?==)/, 'key'],
  [/^(?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error|Throwable)\b/, 'exc'],
  [/^"(?:[^"\\]|\\.)*"/, 'str'],
  [/^\b\d+(?:\.\d+)?\b/, 'num']
];
const JSON_RULES: [RegExp, string][] = [
  [/^"(?:[^"\\]|\\.)*"(?=\s*:)/, 'key'],
  [/^"(?:[^"\\]|\\.)*"/, 'str'],
  [/^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/, 'num'],
  [/^\b(?:true|false|null)\b/, 'kw'],
  [/^[{}[\],:]/, 'punct']
];

export function tokenize(line: string): Token[] {
  if (line.length > 4000) {
    return [{ text: line, cls: '' }];
  }
  const trimmed = line.trimStart();
  if (trimmed.startsWith('{') || trimmed.startsWith('[{')) {
    return withLevelHighlight(scan(line, JSON_RULES));
  }
  if (/^\s+at\s/.test(line) || /^\s*\.\.\.\s*\d+\s+more/.test(line)) {
    return [{ text: line, cls: 'frame' }];
  }
  if (/^\s*Caused by:/.test(line)) {
    return [{ text: line, cls: 'cause' }];
  }
  const tokens: Token[] = [];
  const ts = TS.exec(line);
  let rest = line;
  if (ts) {
    tokens.push({ text: ts[0], cls: 'ts' });
    rest = line.slice(ts[0].length);
  }
  return [...tokens, ...scan(rest, TEXT_RULES)];
}

function scan(input: string, rules: [RegExp, string][]): Token[] {
  const tokens: Token[] = [];
  let plain = '';
  let i = 0;
  while (i < input.length) {
    const rest = input.slice(i);
    let matched = false;
    // only start a token at a word boundary to avoid splitting identifiers
    const prev = i > 0 ? input[i - 1] : ' ';
    if (!/[\w$]/.test(prev) || /^["{}[\],:]/.test(rest)) {
      for (const [re, cls] of rules) {
        const m = re.exec(rest);
        if (m && m[0].length > 0) {
          if (plain) {
            tokens.push({ text: plain, cls: '' });
            plain = '';
          }
          tokens.push({ text: m[0], cls: cls === 'bracket' && m[0].includes('MASKED') ? 'masked' : cls });
          i += m[0].length;
          matched = true;
          break;
        }
      }
    }
    if (!matched) {
      plain += input[i];
      i++;
    }
  }
  if (plain) {
    tokens.push({ text: plain, cls: '' });
  }
  return tokens;
}

/** In JSON lines, color the value of the level field like a level keyword. */
function withLevelHighlight(tokens: Token[]): Token[] {
  for (let i = 0; i < tokens.length; i++) {
    if (tokens[i].cls === 'key' && /^"(level|severity)"$/i.test(tokens[i].text)) {
      const value = tokens.slice(i + 1).find((t) => t.cls === 'str');
      if (value) {
        const v = value.text.replace(/"/g, '').toUpperCase();
        value.cls = v.startsWith('ERR') || v === 'FATAL' ? 'lv-error' : v.startsWith('WARN') ? 'lv-warn' : v === 'INFO' ? 'lv-info' : 'lv-debug';
      }
    }
    if (tokens[i].cls === 'str' && tokens[i].text.includes('[MASKED')) {
      tokens[i].cls = 'masked';
    }
  }
  return tokens;
}
