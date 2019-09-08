(defproject opentracing-clj "0.1.5"
  :description "Opentracing API support for Clojure built on top of opentracing-java."
  :url "https://github.com/alvinfrancis/opentracing-clj"
  :license {:name         "Eclipse Public License"
            :url          "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :source-paths ["src/clj"]
  :plugins [[lein-codox "0.10.4"]]
  :dependencies [[io.opentracing/opentracing-api "0.32.0"]
                 [io.opentracing/opentracing-noop "0.32.0"]
                 [io.opentracing/opentracing-util "0.32.0"]
                 [ring/ring-core "1.7.1"]]
  :codox {:output-path "codox"
          :metadata    {:doc/format :markdown}
          :source-uri  "https://github.com/alvinfrancis/opentracing-clj/blob/v{version}/{filepath}#L{line}"}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :test {:dependencies [[io.opentracing/opentracing-mock "0.32.0"]
                                   [ring/ring-mock "0.3.2"]]}})
