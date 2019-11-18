(ns enron-threading.xform-test
  (:require [clojure.test :refer :all]
            [enron-threading.xform :refer :all]))

(def fixture [{:id "a" :name "dave" :color :blue :size 3.5}
              {:id "a" :name "dave" :color :red  :size 5.5}
              {:id "b" :name "john" :color :gray :size 4.0}
              {:id "c" :name "sven" :color :blue :size 9.9}
              {:id "b" :name "john" :color :blue :size 0.1}
              {:id "a" :name "dave" :color :gray :size 2.2}])

(deftest vectorize-test
  (testing "0-arrity calls"
    (is (= [] (vectorize))))
  (testing "1-arrity calls"
    (is (= [:thing] (vectorize :thing)) "vectorizes non-vectors")
    (is (= [:thing] (vectorize [:thing])) "doesn't touch existing vectors"))
  (testing "2-arrity calls"
    (is (= [1 2 3] (vectorize 1 2 3)) "will vectorize a sequence")
    (testing "seqs of vectors and scalars"
      (is (= [1 2 3] (vectorize [1] 2 3)))
      (is (= [1 2 3] (vectorize [1 2] 3)))
      (is (= [1 2 3] (vectorize [1 2 3]))))))

(deftest denormalize-test
  (let [result (transduce (denormalize :id [:color :size]) conj fixture)]
    (testing "the composition of our result"
      (is (= 3 (count result))
          "Should have 3 results")
      (is (every? #{:id/a :id/b :id/c} (keys result))
          "Should have all composite keys"))

    (testing "the result for Dave"
      (is (= "dave" (get-in result [:id/a :name]))
          "Name should be 'dave'")
      (is (= [:blue :red :gray] (get-in result [:id/a :color]))
          "Should have all colors")
      (is (= [3.5 5.5 2.2] (get-in result [:id/a :size]))
          "Should have all sizes"))

    (testing "a result without repetition"
      (is (= "sven" (get-in result [:id/c :name]))
          "Name should be 'sven'")
      (is (= [:blue] (get-in result [:id/c :color]))
          "Sven only has one color and it's in a vector"))))
