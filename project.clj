(defproject opentracing-clj "0.2.2"
  :description "Opentracing API support for Clojure built on top of opentracing-java."
  :url "https://github.com/alvinfrancis/opentracing-clj"
  :license {:name         "Eclipse Public License"
            :url          "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :source-paths ["src/clj"]
  :plugins [[lein-codox "0.10.7"]]
  :dependencies [[io.opentracing/opentracing-api "0.33.0"]
                 [io.opentracing/opentracing-noop "0.33.0"]
                 [io.opentracing/opentracing-util "0.33.0"]
                 [ring/ring-core "1.8.1"]]
  :codox {:output-path "codox"
          :metadata    {:doc/format :markdown}
          :source-uri  "https://github.com/alvinfrancis/opentracing-clj/blob/v{version}/{filepath}#L{line}"}
  :deploy-repositories [["snapshots" {:url      "https://clojars.org/repo"
                                      :username [:env/clojars_username :gpg]
                                      :password [:env/clojars_password :gpg]}]
                        ["releases"  {:url           "https://clojars.org/repo"
                                      :username      [:env/clojars_username :gpg]
                                      :password      [:env/clojars_password :gpg]
                                      :sign-releases false}]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :test     {:dependencies [[io.opentracing/opentracing-mock "0.33.0"]
                                       [ring/ring-mock "0.4.0"]]}})
