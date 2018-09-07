(defproject opentracing-clj "0.1.0-SNAPSHOT"
  :description "Opentracing API support for Clojure built on top of opentracing-java."
  :url "https://github.com/alvinfrancis/opentracing-clj"
  :license {:name         "Eclipse Public License"
            :url          "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [io.opentracing/opentracing-api "0.31.0"]
                 [io.opentracing/opentracing-noop "0.31.0"]
                 [io.opentracing/opentracing-util "0.31.0"]
                 [ring "1.6.3"]]
  :profiles {:test {:dependencies [[io.opentracing/opentracing-mock "0.31.0"]]}})
