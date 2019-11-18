(defproject enron-threading "0.1.0-SNAPSHOT"
  :description "Analyze and derive email threads"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "0.1.4"]
                 [seancorfield/next.jdbc "1.0.9"]
                 [mysql/mysql-connector-java "5.1.48"]]
  :main ^:skip-aot enron-threading.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
