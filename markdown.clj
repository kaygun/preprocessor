(ns markdown
  (:require [clojure.string :as st]
            [clojure.java.io :as io]))

(defn parse-header [header-line]
  "Parses code block header to determine code visibility and results display."
  (let [trimmed (st/trim (subs header-line 3))]
    (cond
      (re-find #"(?i)hide\s+all" trimmed)
      {:code false :results false}

      (re-find #"(?i)hide" trimmed)
      {:code false :results true}

      (or (re-find #":display" trimmed) (re-find #":code" trimmed))
      (let [disp-match (re-find #":display\s+([:\w]+)" trimmed)
            res-match  (re-find #":results\s+([:\w]+)" trimmed)
            disp-val   (when disp-match (second disp-match))
            res-val    (when res-match (second res-match))
            show-code  (if disp-val
                         (not (or (= disp-val ":false") (= disp-val "false")))
                         true)
            show-res   (if res-val
                         (not (or (= res-val ":false") (= res-val "false")))
                         true)]
        {:code show-code :results show-res})

      :else
      {:code true :results true})))

(defn split-by-double-backtick [line]
  "Splits line on `` into alternating plain-text and code segments."
  (st/split line #"``" -1))

(defn process-inline-code [line]
  "Processes inline ``expr`` code segments, evaluating them in Clojure."
  (let [pieces (split-by-double-backtick line)]
    (if (even? (count pieces))
      line
      (apply str
             (map-indexed
               (fn [idx piece]
                 (if (even? idx)
                   piece
                   (try
                     (str (eval (read-string piece)))
                     (catch Exception e
                       (str "[Error: " (.getMessage e) "]")))))
               pieces)))))

(defn eval-forms [code-str]
  "Sequentially reads and evaluates all Clojure forms in code-str."
  (let [rdr (java.io.PushbackReader. (java.io.StringReader. code-str))]
    (loop [results []]
      (let [form (read rdr false ::eof)]
        (if (= form ::eof)
          results
          (recur (conj results (eval form))))))))

(defn process-file [input-file output-file]
  "Streams input-file line by line, evaluating code blocks and writing to output-file."
  (with-open [rdr (io/reader input-file)
              wtr (io/writer output-file)]
    (let [in-block   (atom false)
          settings   (atom {:code true :results true})
          code-lines (atom [])]
      (doseq [line (line-seq rdr)]
        (if (st/starts-with? line "```")
          (if @in-block
            ;; Closing code fence
            (let [code-str   (st/join "\n" @code-lines)
                  show-code  (:code @settings)
                  show-res   (:results @settings)
                  eval-res   (try
                               (eval-forms code-str)
                               (catch Exception e
                                 [(str "Error: " (.getMessage e))]))]
              (when show-code
                (.write wtr (str "```clojure\n" code-str "\n```\n")))
              (when (and show-code show-res (seq eval-res))
                (.write wtr "\n"))
              (when (and show-res (seq eval-res))
                (.write wtr (str "```clojure\n" (st/join "\n" eval-res) "\n```\n")))
              (reset! in-block false)
              (reset! code-lines []))
            ;; Opening code fence
            (do
              (reset! in-block true)
              (reset! settings (parse-header line))
              (reset! code-lines [])))
          (if @in-block
            (swap! code-lines conj line)
            (.write wtr (str (process-inline-code line) "\n"))))))))

;; Entry point
(let [args *command-line-args*]
  (if (< (count args) 2)
    (do
      (binding [*out* *err*]
        (println "Usage: clj -M markdown.clj <input.mclj> <output.md>"))
      (System/exit 1))
    (process-file (first args) (second args))))
