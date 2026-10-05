import sys
import io
import ast

def parse_header(rest):
    """Parses fence header to determine echo (source display) and results display."""
    block = {'results': True, 'echo': True}
    trimmed = rest.strip()
    if not trimmed:
        return block

    lower = trimmed.lower()
    if "hide all" in lower:
        return {'results': False, 'echo': False}
    if "hide" in lower:
        return {'results': True, 'echo': False}

    if trimmed.startswith("{"):
        try:
            settings = ast.literal_eval(trimmed)
            if isinstance(settings, dict):
                for k, v in settings.items():
                    if isinstance(v, str):
                        v_lower = v.lower()
                        if v_lower == 'false':
                            val = False
                        elif v_lower in ('true', 'show'):
                            val = True
                        else:
                            val = bool(v)
                    else:
                        val = bool(v)
                    block[k] = val
        except Exception:
            pass

    return block

def execute_code(command, exec_context):
    """Executes Python code in persistent context, capturing stdout and expression values."""
    res = io.StringIO()
    old_stdout = sys.stdout
    sys.stdout = res
    try:
        try:
            parsed = ast.parse(command)
            if parsed.body and isinstance(parsed.body[-1], ast.Expr):
                last_expr = parsed.body.pop()
                if parsed.body:
                    code_obj = compile(parsed, filename="<markdown-cell>", mode="exec")
                    exec(code_obj, exec_context)
                expr_obj = compile(ast.Expression(last_expr.value), filename="<markdown-cell>", mode="eval")
                val = eval(expr_obj, exec_context)
                if val is not None:
                    print(repr(val))
            else:
                exec(command, exec_context)
        except Exception as e:
            res.write(f"Execution error: {e}\n")
    finally:
        sys.stdout = old_stdout

    return res.getvalue()

def split_by_double_backtick(line):
    """Splits line on `` into alternating plain-text and code segments."""
    parts = []
    start = 0
    delim = "``"
    while start <= len(line):
        idx = line.find(delim, start)
        if idx != -1:
            parts.append(line[start:idx])
            start = idx + len(delim)
        else:
            parts.append(line[start:])
            break
    return parts

def process_inline_code(line, outfile, exec_context):
    """Processes inline double backtick ``expr`` expressions and preserves line endings."""
    has_newline = line.endswith("\n")
    clean_line = line.rstrip("\r\n")

    parts = split_by_double_backtick(clean_line)
    if len(parts) % 2 == 0:
        # Unmatched delimiter, leave line untouched
        outfile.write(line)
        return

    for i, part in enumerate(parts):
        if i % 2 == 0:
            outfile.write(part)
        else:
            try:
                res = eval(part.strip(), exec_context)
                outfile.write(str(res))
            except Exception as e:
                outfile.write(f"[Error: {e}]")

    if has_newline:
        outfile.write("\n")

def process_markdown(input_file, output_file):
    """Processes a markdown file line by line, executing code blocks and formatting output."""
    exec_context = {}  # Persistent execution context across blocks
    inside_code_block = False
    block_settings = {'results': True, 'echo': True}
    command_lines = []

    with open(input_file, "r") as infile, open(output_file, "w") as outfile:
        for line in infile:
            if line.startswith("```"):
                if not inside_code_block:
                    # Opening code fence
                    inside_code_block = True
                    command_lines = []
                    rest = line[3:]
                    block_settings = parse_header(rest)
                else:
                    # Closing code fence
                    code = "".join(command_lines)
                    show_echo = block_settings.get('echo', True)
                    show_results = block_settings.get('results', True)

                    output = execute_code(code, exec_context)

                    if show_echo:
                        outfile.write("```python\n")
                        outfile.write(code)
                        if not code.endswith("\n"):
                            outfile.write("\n")
                        outfile.write("```\n")

                    if show_echo and show_results and output.strip():
                        outfile.write("\n")

                    if show_results and output.strip():
                        outfile.write("```python\n")
                        outfile.write(output)
                        if not output.endswith("\n"):
                            outfile.write("\n")
                        outfile.write("```\n")

                    inside_code_block = False
                    command_lines = []
                    block_settings = {'results': True, 'echo': True}
            else:
                if inside_code_block:
                    command_lines.append(line)
                else:
                    process_inline_code(line, outfile, exec_context)

        outfile.flush()

if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.stderr.write("Usage: python3 markdown.py <input.mpy> <output.md>\n")
        sys.exit(1)

    process_markdown(sys.argv[1], sys.argv[2])
