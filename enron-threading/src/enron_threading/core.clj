(ns enron-threading.core
  (:require [clojure.data.csv :as csv]
            [clojure.string :as s]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [enron-threading.xform :as xform])
  (:gen-class))

;;;;; Database Setup & Functions

;; TODO: update details from env or args
(def db {:dbtype "mysql"
         :dbname "enron"
         :user "neo4j"
         :password "neo4j"
         :host "192.168.56.101"
         :port 33060})

;; Make MySQL stream results ASAP
(def jdbc-opts {:fetch-size (Integer/MIN_VALUE)
                :concurrency :read-only
                :result-type :forward-only})


;; Ordering by date ascending saves us processing later ;-)
(def sql "SELECT mid, subject, date FROM message ORDER BY date ASC")

(defn prepare
  [ds]
  (jdbc/plan ds [sql] jdbc-opts))


;;;;; Message processing

(defn email-type
  "Extract the email 'type'...is it a reply? forward? etc."
  [subject]
  (if (and (string? subject) (> (count subject) 3))
    (let [part (subs (s/lower-case subject) 0 3)]
      (cond (or (= "re:" part) (= "re " part)) :re
            (or (= "fw:" part) (= "fw " part)) :fw
            :else :original))))

(defn email-root
  "Find the root subject, i.e. the part without a re:/fw: prefix"
  [subject]
  (let [root (last (s/split subject #"(?i)^(re|fw)[: ]"))]
    (if (and (some? root) (< 0 (count root)))
      (s/trim root))))

(defn parse-subject
  [subject]
  (let [t (email-type subject)
        r (email-root subject)]
    {:email-type t :subject r}))


;;;; Transducing routines and our Composition

(defn simplify-result
  "Translate a JDBC result from our query to a simpler Map"
  [r]
  (let [{:keys [message/mid message/subject message/date]} r
        s (parse-subject subject)
        d (.toLocalDateTime date)]
    (merge {:mid mid :date d} s)))

(defn valid-thread?
  [t]
  (and (< 1 (count (:mid t)))
       (< (count (filter #{:original} (:email-type t))) 2)))

(defn thread->seq
  "Take a reduced thread of collected emails and make an ordered
  sequence based on date"
  [t]
  (let [{:keys [mid date email-type subject]} t]
    (apply mapv vector [mid date email-type])))

(defn tuple?
  [x]
  (= 2 (count x)))

(def threading-tx
  (comp (map simplify-result)                      ;; turn into clj map
        (filter #(some? (:subject %)))             ;; drop bogus subjects
        (xform/denormalize :subject
                       [:mid :date :email-type]))) ;; construct threads

(def inflating-tx
  (comp (map second)                  ;; take the threads from our master map
        (filter valid-thread?)        ;; drop threads that look like crap
        (map thread->seq)             ;; turn into an ordered sequence of emails
        (map #(partition-all 2 1 %))  ;; pair up emails we want to connect
        (map #(filter tuple? %))))    ;; drop non-pairs

;;;; CSV Stuff
(def header ["from" "to"])

(defn tuple->row
  [tuple]
  (let [[left right] tuple
        from (first left)
        to (first right)]
    [from to]))

;;;; MAIN

(defn get-thread-coll
  [query]
  (let [thread-map (transduce threading-tx conj [] query)
        threads (into [] (eduction inflating-tx thread-map))]
    (eduction (map #(map tuple->row %)) threads)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [ds (jdbc/get-datasource db)
        query (prepare ds)
        data  (get-thread-coll query)]
    (csv/write-csv *out* [["from" "to"]])
    (doseq [row data] (csv/write-csv *out* row))))
