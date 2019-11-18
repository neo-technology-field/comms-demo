(require '[clojure.data.csv :as csv]
         '[clojure.string :as s]
         '[enron-threading.core :as e]
         '[enron-threading.xform :as x]
         '[next.jdbc :as jdbc])

(def ds (jdbc/get-datasource e/db))

;;; What's the data look like coming from our SQL?
(def query (e/prepare ds))

; sample the first 10000 rows, convert to maps, denormalize
(def m (transduce e/threading-tx conj [] query))
(count m)

; transform our threads into sequences of emails
(def r (into [] (eduction e/inflating-tx m)))
(count r)
(nth r 235)

(def t (nth r 235))
(map e/tuple->row t)

(transduce (comp (take 2) (map #(map e/tuple->row %))) println r)

(defn write!
  [w]
  (fn
    ([]
      nil)
    ([x]
     (println "writing x" x)
     (if (some? x) (csv/write-csv w x)))
    ([x y]
     (println "asked to write x" x "y" y))))

(with-open [writer (clojure.java.io/writer "/tmp/junk.csv")]
  (let [coll (eduction (comp (take 10) (map #(map e/tuple->row %))) r)]
    (doseq [row coll]
      (csv/write-csv writer row))))
