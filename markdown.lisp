(defun helper (x) (eval (read-from-string x)))

(defun process-inline-code (line out)
  (let ((pieces (ppcre:split "``" line)))
    (loop
       for x in pieces
       for y from 0 below (length pieces) do
	 (format out "~a" (if (evenp y) x (helper x))))
    (format out "~%")))

(defun process-header (line)
  (cond ((ppcre:scan "hide all" line) (list :flag t :code nil :results nil))
	((ppcre:scan "hide" line) (list :flag t :code nil :results t))
	(t (list :flag t :code t :results t))))

(defun process-text (line out register)
  (if (ppcre:scan "^```" line)
      (process-header line)
      (let ()
	(process-inline-code line out)
	register)))

(let (code-buffer)
  (defun process-code (line out register)
    (if (ppcre:scan "^```" line)
	(let ((results (helper (format nil "(list ~{~a~%~})" code-buffer))))
	  (if (getf register :code)
	      (let () (format out "```lisp~%~{~a~%~}```~%" code-buffer)
		      (if (getf register :results)
		          (format out "```lisp~%~{~a~%~}```~%" results)))
	      (if (getf register :results)
		  (format out "~{~a~%~}" results)))
	  (setf code-buffer nil)
	  (list :flag nil))
	(let ()
	  (setf code-buffer
		(append code-buffer (list line)))
	  register))))

(with-open-file (in (elt *posix-argv* 1)
		    :direction :input)
  (with-open-file (out (elt *posix-argv* 2)
		       :direction :output
		       :if-exists :supersede
		       :if-does-not-exist :create)
    (let ((register (list :flag nil)))
      (do ((line (read-line in nil) (read-line in nil)))
	  ((null line) nil)
	(setf register      
	      (if (getf register :flag)
		  (process-code line out register)
		  (process-text line out register)))))))
