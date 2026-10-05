import sys
import io
import ast

try:
    from sage.all import *
    from sage.repl.preparse import preparse
except ImportError:
    def preparse(x):
        return x

def parse_header(rest):
    """Parses fence header to determine echo (source display) and results display."""
    block = {'results': True, 'echo': True, 'is_sage': True}
    trimmed = rest.strip()
    if not trimmed:
        return block

    lower = trimmed.lower()
    tokens = lower.split()

    # Check if block explicitly specifies a non-Sage language
    non_sage_tokens = [t for t in tokens if t not in ('sage', 'sagemath', 'hide', 'all') and not t.startswith('{')]
    if non_sage_tokens and not any(t in ('sage', 'sagemath') for t in tokens):
        block['is_sage'] = False
        return block

    if "hide all" in lower:
        block['results'] = False
        block['echo'] = False
        return block

    if "hide" in lower:
        block['results'] = True
        block['echo'] = False
        return block

    if "{" in trimmed:
        try:
            brace_start = trimmed.find("{")
            brace_end = trimmed.rfind("}")
            if brace_start != -1 and brace_end != -1:
                dict_str = trimmed[brace_start:brace_end + 1]
                settings = ast.literal_eval(dict_str)
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
    """Executes SageMath code in persistent context, capturing stdout and expression values."""
    res = io.StringIO()
    old_stdout = sys.stdout
    sys.stdout = res
    try:
        try:
            preparsed = preparse(command)
            parsed = ast.parse(preparsed)
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
                exec(preparsed, exec_context)
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
    """Processes inline double backtick ``expr`` expressions with Sage preparsing."""
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
                expr = preparse(part.strip())
                res = eval(expr, exec_context)
                outfile.write(str(res))
            except Exception as e:
                outfile.write(f"[Error: {e}]")

    if has_newline:
        outfile.write("\n")

def process_markdown(input_file, output_file):
    """Processes a markdown file line by line, executing SageMath code blocks and formatting output."""
    # Persistent context seeded with Sage globals
    exec_context = dict(globals())
    inside_code_block = False
    is_sage_block = True
    raw_fence_line = ""
    block_settings = {'results': True, 'echo': True, 'is_sage': True}
    command_lines = []

    with open(input_file, "r") as infile, open(output_file, "w") as outfile:
        for line in infile:
            if line.startswith("```"):
                if not inside_code_block:
                    # Opening code fence
                    inside_code_block = True
                    command_lines = []
                    raw_fence_line = line
                    rest = line[3:]
                    block_settings = parse_header(rest)
                    is_sage_block = block_settings.get('is_sage', True)

                    if not is_sage_block:
                        outfile.write(line)
                else:
                    # Closing code fence
                    if is_sage_block:
                        code = "".join(command_lines)
                        show_echo = block_settings.get('echo', True)
                        show_results = block_settings.get('results', True)

                        output = execute_code(code, exec_context)

                        if show_echo:
                            outfile.write("```sage\n")
                            outfile.write(code)
                            if not code.endswith("\n"):
                                outfile.write("\n")
                            outfile.write("```\n")

                        if show_echo and show_results and output.strip():
                            outfile.write("\n")

                        if show_results and output.strip():
                            outfile.write("```sage\n")
                            outfile.write(output)
                            if not output.endswith("\n"):
                                outfile.write("\n")
                            outfile.write("```\n")
                    else:
                        outfile.write(line)

                    inside_code_block = False
                    is_sage_block = True
                    command_lines = []
                    raw_fence_line = ""
                    block_settings = {'results': True, 'echo': True, 'is_sage': True}
            else:
                if inside_code_block:
                    if is_sage_block:
                        command_lines.append(line)
                    else:
                        outfile.write(line)
                else:
                    process_inline_code(line, outfile, exec_context)

        outfile.flush()

if len(sys.argv) < 3:
    sys.stderr.write("Usage: sage markdown.sage <input.msage> <output.md>\n")
    sys.exit(1)

process_markdown(sys.argv[1], sys.argv[2])
