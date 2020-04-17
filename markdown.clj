(require '[clojure.string :as st]
         '[clojure.java.io :as io])

(defn process [results]
   (reduce (fn [a b] (str a "\n" b)) results))

(defn read-text [text]
   {:results :true :output text})

(defn read-code [command]
   (let [[head code] (st/split command #":code")]
      (merge 
         (eval (read-string (str "{" head "}")))
         {:code code}
         {:output (-> (str "(list " code ")")
                      read-string
                      eval
                      process)})))

(let* [txt (-> *command-line-args*
               (nth 0)
               slurp
               (st/split #"```"))
       out (nth *command-line-args* 1)
       res (map (fn [x y] 
                    (if (odd? y) 
                       (read-code x)
                       (read-text x)))
                txt 
                (range))]
    (doseq [x res]
       (spit out
          (if (x :display)
             (str "```clojure" 
                  (x :code) 
                  "```\n"
                  (if (x :results) 
                     (str "```clojure\n" (x :output) "\n```")
                     ""))
             (str (x :output)))
          :append true)))
