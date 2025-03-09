(defun eval-string (x)
  "Evaluates a string as Lisp code."
  (eval (read-from-string x)))

(defun process-inline-code (line out)
  "Processes and writes inline code fragments in a line."
  (let ((pieces (ppcre:split "``" line)))
    (loop for x in pieces
          for i from 0
          do (format out "~a" (if (evenp i) x (eval-string x))))
    (format out "~%")))

(defun parse-header (line)
  "Parses a header line to determine visibility settings."
  (cond ((ppcre:scan "hide all" line) (list :flag t :code nil :results nil))
        ((ppcre:scan "hide" line) (list :flag t :code nil :results t))
        (t (list :flag t :code t :results t))))

(defun process-text (line out register)
  "Processes a text line, checking if it's a header or inline code."
  (if (ppcre:scan "^```" line)
      (parse-header line)
      (progn
        (process-inline-code line out)
        register)))

(defvar *code-buffer* nil)

(defun process-code (line out register)
  "Processes code blocks, evaluates them, and writes results if required."
  (if (ppcre:scan "^```" line)
      (let ((results (eval-string (format nil "(list ~{~a~%~})" *code-buffer*))))
        (when (getf register :code)
          (format out "```lisp~%~{~a~%~}```~%" *code-buffer*))
        (when (getf register :results)
          (format out "```lisp~%~{~a~%~}```~%" results)))
      (setf *code-buffer* nil)
      (list :flag nil))
    (push line *code-buffer*)
    register))

(defun process-file (input-file output-file)
  "Reads an input file, processes its lines, and writes results to the output file."
  (with-open-file (in input-file :direction :input)
    (with-open-file (out output-file :direction :output
                                      :if-exists :supersede
                                      :if-does-not-exist :create)
      (let ((register (list :flag nil)))
        (do ((line (read-line in nil) (read-line in nil)))
            ((null line) nil)
          (setf register
                (if (getf register :flag)
                    (process-code line out register)
                    (process-text line out register))))))))

;; Entry point
(process-file (elt *posix-argv* 1) (elt *posix-argv* 2))


