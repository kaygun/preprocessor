# A Ployglot Preprocessor for Literate Programming

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
Depending on the $LANG it will process the markdown file. **Nothing gets 
touched** except the embedded lisp/scala/clojure code.

    mlisp $FILE $TITLE

You may provide a title at the prompt, or you may add it in the markdown
file in the preamble as

    ---
    title: $TITLE
    ---

For clojure use '.mclj', for lisp use '.mlisp', for python use '.mpy'
and for scala use '.msc' as extensions. The program will watch the 
$FILE and when it is modified, it will process and spit out an HTML 
file until it is killed.

## Short Tutorial

Code blocks are marked with `` ``` `` and everything in between is treated as
a $LANG code block, and will be executed as such. One can supply a `hide` 
keyword to the header. In that case, the source is not going to be displayed 
but the results are going to be displayed. If you pass a `hide all` then both 
the source and the results are not going to be displayed.  If you need a piece 
of code executed within text (as in $ vs. $$ in LaTeX) then use `` `` `` in 
the text.  See the example `example.txt` file.

