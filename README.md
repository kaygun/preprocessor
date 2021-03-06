# A Text Preprocessor for Markdown

## Why?

This application is born out of a necessity: I needed something 
light-weight and portable that would process a text file and
evaluate the embedded Common Lisp code without touching the rest
of the text.  My constraints were:

* Light-weight: no servers, no swank, no emacs, no nothing...
* Should be streaming: should not slurp the whole text before
  it starts processing.  It should process it line by line.
* Should work with vanilla $LANG without using heavy libraries
  or frameworks.

There is a shell file that dispatches the correct preprocessor.

    mlisp $LANG $FILE

Use $FILE without the extension.  That's it.

You can process a markdown file and **nothing gets touched** except 
the embedded lisp/scala/clojure code.

## Short Tutorial

Code blocks are marked with `` ``` `` and everything in between is treated as
a $LANG code block, and will be executed as such. One can supply a `hide` 
keyword to the header. In that case, the source is not going to be displayed 
but the results are going to be displayed. If you pass a `hide all` then both 
the source and the results are not going to be displayed.  If you need a piece 
of code executed within text (as in $ vs. $$ in LaTeX) then use `` `` `` in 
the text.  See the example `example.txt` file.

