;;;; markdown.lisp — Polyglot literate programming preprocessor for Common Lisp
;;;;
;;;; Evaluates embedded Common Lisp code in markdown files.
;;;; Fenced blocks (```) are evaluated sequentially in persistent environment.
;;;; Directives on opening fence:
;;;;   ```hide all  -> evaluate silently (no source, no results)
;;;;   ```hide      -> evaluate and show results only
;;;;   ```          -> show source and results
;;;; Inline code:
;;;;   ``(expr)``   -> evaluated and spliced into text

(defun starts-with-p (str prefix)
  "Returns T if STR begins with PREFIX."
  (let ((plen (length prefix)))
    (and (>= (length str) plen)
         (string= str prefix :end1 plen))))

(defun parse-header (line)
  "Parses a fence header line to determine visibility settings."
  (cond ((search "hide all" line :test #'char-equal)
         (list :code nil :results nil))
        ((search "hide" line :test #'char-equal)
         (list :code nil :results t))
        (t
         (list :code t :results t))))

(defun split-by-double-backtick (line)
  "Splits LINE by double backticks (``) into alternating text and code pieces."
  (let ((pieces nil)
        (start 0)
        (len (length line)))
    (loop
      (let ((pos (search "``" line :start2 start)))
        (if pos
            (progn
              (push (subseq line start pos) pieces)
              (setf start (+ pos 2)))
            (progn
              (push (subseq line start len) pieces)
              (return (nreverse pieces))))))))

(defun eval-string (x)
  "Reads and evaluates a single Lisp form from a string."
  (eval (read-from-string x)))

(defun process-inline-code (line out)
  "Processes inline code fragments enclosed by double backticks (``)."
  (let ((pieces (split-by-double-backtick line)))
    (if (evenp (length pieces))
        ;; Unmatched delimiter: output line as-is
        (format out "~a~%" line)
        (progn
          (loop for piece in pieces
                for i from 0
                do (format out "~a"
                           (if (evenp i)
                               piece
                               (handler-case
                                   (eval-string piece)
                                 (error (e)
                                   (format nil "[Error: ~a]" e))))))
          (terpri out)))))

(defun eval-forms (lines)
  "Sequentially reads and evaluates all forms in LINES.
Returns a list of the non-nil results of top-level expressions."
  (let ((code-str (format nil "~{~a~%~}" lines)))
    (with-input-from-string (s code-str)
      (loop for form = (read s nil :eof)
            until (eq form :eof)
            collect (eval form)))))

(defun process-file (input-file output-file)
  "Reads INPUT-FILE line by line and writes processed markdown to OUTPUT-FILE."
  (with-open-file (in input-file :direction :input)
    (with-open-file (out output-file :direction :output
                                      :if-exists :supersede
                                      :if-does-not-exist :create)
      (let ((in-code-block nil)
            (code-buffer nil)
            (block-settings nil))
        (do ((line (read-line in nil nil) (read-line in nil nil)))
            ((null line) nil)
          (if in-code-block
              (if (starts-with-p line "```")
                  ;; Closing code fence
                  (let* ((lines (nreverse code-buffer))
                         (show-code (getf block-settings :code))
                         (show-results (getf block-settings :results))
                         (results (handler-case
                                      (eval-forms lines)
                                    (error (e)
                                      (list (format nil "Error: ~a" e))))))
                    (when show-code
                      (format out "```lisp~%~{~a~%~}```~%" lines))
                    (when (and show-code show-results results)
                      (terpri out))
                    (when (and show-results results)
                      (format out "```lisp~%~{~a~%~}```~%" results))
                    (setf in-code-block nil
                          code-buffer nil
                          block-settings nil))
                  ;; Accumulate line in current code block
                  (push line code-buffer))
              (if (starts-with-p line "```")
                  ;; Opening code fence
                  (setf in-code-block t
                        code-buffer nil
                        block-settings (parse-header line))
                  ;; Normal text line
                  (process-inline-code line out))))))))

;; Entry point
(let ((args (rest sb-ext:*posix-argv*)))
  (when (and args (string= (first sb-ext:*posix-argv*) "sbcl"))
    ;; When running via sbcl --script, args may differ slightly
    nil)
  (let ((in-file (first args))
        (out-file (second args)))
    (unless (and in-file out-file)
      (format *error-output* "Usage: sbcl --script markdown.lisp <input.mlsp> <output.md>~%")
      (sb-ext:exit :code 1))
    (handler-case
        (process-file in-file out-file)
      (error (e)
        (format *error-output* "markdown.lisp error: ~a~%" e)
        (sb-ext:exit :code 1)))))
