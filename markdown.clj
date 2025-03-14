(ns prep
  (:require [clojure.string :as st]
            [clojure.java.io :as io]))

(defn join-lines [lines]
  "Joins a sequence of lines with newlines."
  (apply str (interpose "\n" lines)))

(defn wrap-text [text]
  "Wraps plain text in a result map."
  {:results true :output text})

(defn execute-code [command]
  "Processes a code block, evaluates it, and returns structured results."
  (let [[meta code] (st/split command #":code" 2)
        metadata-map (eval (read-string (str "{" meta "}")))]
    (merge metadata-map
           {:code code
            :output (join-lines (eval (read-string (str "(list " code ")"))))})))

(defn process-input [input output-file]
  "Reads input, processes it as alternating text and code blocks, and writes to output."
  (let [segments (st/split (slurp input) #"```")
        results  (map-indexed 
                   (fn [idx segment]
                     (if (odd? idx) 
                       (execute-code segment)
                       (wrap-text segment)))
                   segments)]
    (doseq [result results]
      (spit output-file
            (if (:display result)
              (str "```clojure\n" (:code result) "\n```\n"
                   (when (:results result)
                     (str "```clojure\n" (:output result) "\n```")))
              (:output result))
            :append true))))

;; Entry point
(let [args *command-line-args*
      input-file (first args)
      output-file (second args)]
  (process-input input-file output-file))

