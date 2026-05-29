;;;; markdown.lisp — literate-programming preprocessor.
;;;;
;;;; Invoked as:
;;;;   sbcl --script markdown.lisp <input> <output>
;;;;
;;;; Reads a Markdown-ish file. Inline `` ...lisp... `` is evaluated and
;;;; spliced in. Fenced ```...``` blocks are evaluated as a whole; the
;;;; opening fence may carry directives:
;;;;   ```hide all   -> evaluate silently
;;;;   ```hide       -> show results only
;;;;   ```           -> show source and results
;;;;
;;;; SECURITY: this program EVALUATES code from the input file. Only run
;;;; it on input you trust.

(require :cl-ppcre)

(defpackage :litprog
  (:use :cl)
  (:local-nicknames (:re :cl-ppcre)))

(in-package :litprog)

;;; --- evaluation ---------------------------------------------------------

(defun eval-string (string)
  "Read and evaluate STRING as Lisp. Returns the value of the last form."
  (eval (read-from-string string)))

(defun eval-lines (lines)
  "Evaluate LINES (a list of strings) as a sequence of top-level forms.
Returns a list of their values, one per form."
  (with-input-from-string (s (format nil "~{~a~%~}" lines))
    (loop for form = (read s nil 's)
          until (eq form 's)
          collect (eval form))))

;;; --- header parsing -----------------------------------------------------

(defstruct directives
  (show-source  t)
  (show-results t))

(defun parse-fence (line)
  "Parse an opening fence line into a DIRECTIVES struct."
  (cond ((re:scan "hide all" line) (make-directives :show-source nil
                                                    :show-results nil))
        ((re:scan "hide"     line) (make-directives :show-source nil
                                                    :show-results t))
        (t                         (make-directives))))

(defun fence-line-p (line)
  (re:scan "^```" line))

;;; --- output -------------------------------------------------------------

(defun emit-text-line (line out)
  "Write LINE to OUT, evaluating any `` ...`` inline code."
  (loop for piece in (re:split "``" line)
        for i from 0
        do (format out "~a" (if (evenp i) piece (eval-string piece))))
  (terpri out))

(defun emit-block (out lines directives)
  "Emit a finished code block according to DIRECTIVES."
  (let* ((show-src  (directives-show-source  directives))
         (show-res  (directives-show-results directives))
         (results   (when (or show-src show-res) (eval-lines lines))))
    (cond
      ((and show-src show-res)
       (format out "```lisp~%~{~a~%~}```~%" lines)
       (format out "```lisp~%~{~a~%~}```~%" results))
      (show-src
       (format out "```lisp~%~{~a~%~}```~%" lines))
      (show-res
       (format out "~{~a~%~}" results))
      (t nil))))

;;; --- main loop ----------------------------------------------------------

(defun process-stream (in out)
  (let ((in-block   nil)
        (directives nil)
        (buffer     '()))                ; collected in reverse
    (loop for line = (read-line in nil nil)
          while line do
          (cond
            ((and in-block (fence-line-p line))
             (emit-block out (nreverse buffer) directives)
             (setf in-block nil
                   buffer   '()))
            ((and (not in-block) (fence-line-p line))
             (setf directives (parse-fence line)
                   in-block   t
                   buffer     '()))
            (in-block
             (push line buffer))
            (t
             (emit-text-line line out))))
    (when in-block
      (warn "Input ended inside an unclosed code block."))))

(defun process-file (input-path output-path)
  (with-open-file (in  input-path  :direction :input)
    (with-open-file (out output-path :direction :output
                                     :if-exists :supersede
                                     :if-does-not-exist :create)
      (process-stream in out))))

;;; --- script entry point -------------------------------------------------

(defun main (argv)
  (unless (>= (length argv) 3)
    (format *error-output*
            "usage: markdown.lisp <input> <output>~%")
    (sb-ext:exit :code 2))
  (handler-case
      (process-file (elt argv 1) (elt argv 2))
    (error (c)
      (format *error-output* "markdown.lisp: ~a~%" c)
      (sb-ext:exit :code 1))))

(main sb-ext:*posix-argv*)
