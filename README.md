# Polyglot Preprocessor for Literate Programming

A lightweight, portable preprocessor designed for literate programming. It allows you to embed executable code within Markdown files, evaluates it, and generates formatted output (Markdown and HTML).

## Overview

This tool was created to provide a simple way to evaluate embedded code blocks in text files without the need for complex IDEs or heavy frameworks. It supports multiple languages and processes files line-by-line in a streaming fashion.

### Key Features

*   **Streaming Processing:** Processes files line-by-line, making it memory-efficient and suitable for large files.
*   **Persistent Context:** Execution state (variables, functions, imports) is maintained across different code blocks within the same file.
*   **Polyglot Support:** Includes preprocessors for:
    *   **Common Lisp** (`.mlsp`) using SBCL.
    *   **Python** (`.mpy`) using Python 3.
    *   **Clojure** (`.mclj`) using `clj`.
    *   **Scala** (`.msc`) using `scala`.
*   **Visibility Control:** Use directives to show/hide source code and evaluation results.
*   **HTML Generation:** Automatically triggers `pandoc` to convert the processed Markdown into a styled HTML document.

## Usage

The main entry point is the `mlisp` shell script.

```bash
./mlisp $FILE [$TITLE]
```

*   **$FILE:** The input file with the appropriate extension (`.mlsp`, `.mpy`, `.mclj`, or `.msc`).
*   **$TITLE:** (Optional) The title for the generated HTML document. Alternatively, you can specify it in a YAML preamble:

```markdown
---
title: My Document Title
---
```

The script generates:
1.  A standard Markdown file (`.md`) with the code evaluated.
2.  A standalone HTML file (`.html`) using Pandoc with MathJax support and custom CSS.

## Syntax

### Code Blocks

Fenced code blocks are marked with ` ``` `. They are executed as a whole. You can control the output using directives in the opening fence:

*   **Default:** ` ``` ` — Displays both the source code and the results in the output.
*   **Hide Source:** ` ``` hide ` — Executes the code and displays only the results.
*   **Hide All:** ` ``` hide all ` — Executes the code silently (no source or results in the output). Useful for setup/initialization.

### Inline Evaluation

Evaluate expressions directly within your text using double backticks: ` ``expression`` `. The result of the expression is spliced into the text.

## Dependencies

*   **Pandoc:** Used for final HTML conversion.
*   **Language Runtimes:** SBCL (for Lisp), Python 3, Clojure, or Scala as needed.
*   **Common Lisp:** Requires the `cl-ppcre` library.

## Project Structure

*   `mlisp`: The dispatcher shell script.
*   `markdown.lisp`, `markdown.py`, `markdown.clj`, `markdown.sc`: Language-specific preprocessor implementations.
*   `mlisp.css`: Default styling for the generated HTML.
*   `example.*`: Example documents for each supported language.

---
**Security Warning:** This preprocessor executes arbitrary code found in the input files. Only process files from trusted sources.
