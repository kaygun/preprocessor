import sys
import io
import ast

def process_markdown(input_file, output_file):
    """Processes a markdown file, executing code blocks and formatting output."""
    with open(input_file, "r") as infile:
        lines = infile.readlines()

    block = {'results': True, 'echo': True}
    inside_code_block = False
    command = ""
    exec_context = {}  # Persistent execution context across blocks

    with open(output_file, "w") as outfile:
        for line in lines:
            if line.startswith("```"):
                if not inside_code_block:
                    # Start of a code block
                    command = ""
                    rest = line[3:].strip()

                    if rest:  # Ensure it's not empty
                        try:
                            settings = ast.literal_eval(rest) if rest.startswith("{") else {}
                            if isinstance(settings, dict):
                                block.update(settings)
                        except Exception as e:
                            outfile.write(f"\nError parsing block settings: {e}\n")

                    if block['echo']:
                        outfile.write("```python\n")
                else:
                    # End of a code block
                    if block['echo']:
                        outfile.write("```\n")
                        if block['results']:
                            outfile.write("\n```python\n")

                    # Capture and execute the code
                    res = io.StringIO()
                    sys.stdout = res

                    try:
                        exec(command, exec_context)  # Use persistent execution context
                    except Exception as e:
                        res.write(f"Execution error: {e}\n")

                    sys.stdout = sys.__stdout__

                    if block['results']:
                        outfile.write(res.getvalue())
                        if block['echo']:
                            outfile.write("```\n")

                inside_code_block = not inside_code_block
                outfile.flush()
                continue

            if inside_code_block:
                command += line
                if block['echo']:
                    outfile.write(line)
            else:
                process_inline_code(line, outfile, exec_context)

def process_inline_code(line, outfile, exec_context):
    """Processes inline backticks and executes embedded Python expressions."""
    parts = line.strip("\n").split("`")
    num_parts = len(parts)

    if num_parts == 1:
        outfile.write(line)
    else:
        for i, part in enumerate(parts):
            if i % 2 == 0:
                outfile.write(part)
            else:
                try:
                    outfile.write(str(eval(part, exec_context)))  # Use shared context
                except Exception as e:
                    outfile.write(f"[Error: {e}]")
    outfile.flush()

if len(sys.argv) < 3:
   print("Usage: python script.py input.md output.md")
   sys.exit(1)

process_markdown(sys.argv[1], sys.argv[2])

