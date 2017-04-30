(require '[clojure.string :as st])

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
       res (map (fn [x y] 
                    (if (odd? y) 
                       (read-code x)
                       (read-text x)))
                txt 
                (range (count txt)))]
    (doseq [x res]
       (println 
          (if (x :display)
             (str "```clojure" 
                  (x :code) 
                  "```\n"
                  (if (x :results) 
                     (str "```clojure\n" (x :output) "\n```\n")
                     ""))
             (str (x :output))))))
