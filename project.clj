(defproject mantis_conn "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [environ "1.1.0"]
                 [clj-time "0.14.2"]
                 [cheshire "5.8.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [clj-http "3.7.0"]]
  :main ^:skip-aot mantis-conn.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
