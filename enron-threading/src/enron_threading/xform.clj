(ns enron-threading.xform)

(defn vectorize
  ([] [])
  ([x] (if (vector? x) x [x]))
  ([x & xs] (into [] (mapcat vectorize (cons x xs)))))


(defn denormalize
  "Create a transducer that denormalizes a sequence of maps through reduction"
  [id-key join-keys]
  (fn [rf]
    (let [state (volatile! {})]
      (fn
        ([]
         (rf))
        ([result]
         (rf result))
        ([result input]
         (let [prior @state
               id (keyword (name id-key) (str (input id-key)))
               parts (mapcat #(vector (first %) (vectorize (second %)))
                          (into [] (select-keys input join-keys)))
               oldparts (select-keys (get prior id {}) join-keys)
               new  (hash-map id (merge input
                                        (merge-with vectorize
                                                    oldparts
                                                    (apply hash-map parts))))]
           (vswap! state #(merge-with conj % new))
           (rf @state)))))))
