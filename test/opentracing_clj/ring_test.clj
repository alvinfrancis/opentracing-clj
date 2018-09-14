(ns opentracing-clj.ring-test
  (:require
   [clojure.test :refer :all]
   [clojure.walk :as walk]
   [ring.mock.request :as mock]
   [opentracing-clj.core :as tracing]
   [opentracing-clj.propagation :as propagation]
   [opentracing-clj.ring :refer :all]
   [opentracing-clj.test-utils :as utils])
  (:import
   [io.opentracing.mock MockTracer]))

(use-fixtures :each utils/with-mock-tracer)

(defn mock-handler
  [r]
  {:status 200})

(deftest wrap-opentracing-test
  (testing "wrap-opentracing-test"

    (let [uri          "/test"
          method       :get
          base-request (mock/request method uri)
          client-span  {:name "client"}
          response     (volatile! nil)]

      (testing "pass-through"
        (tracing/with-span [s client-span]
          (let [handler (-> mock-handler (wrap-opentracing))
                headers (propagation/inject (tracing/context s) :http)
                request (reduce (fn [r header]
                                  (mock/header r (key header) (val header)))
                                base-request headers)]
            (vreset! response (handler request))
            (is (= 200 (:status @response))))))

      (let [spans (.finishedSpans tracing/*tracer*)]
        (testing "spans recorded"
          (is (= 2 (count spans))))

        (testing "operation name"
          (is (= (.operationName (nth spans 0))
                 (default-op-name base-request))))

        (testing "tags set"
          (is (= (walk/keywordize-keys (into {} (.tags (nth spans 0))))
                 (merge (default-request-tags base-request)
                        (default-response-tags @response)))))

        (testing "client finish"
          (is (= (.operationName (nth spans 1))
                 (:name client-span))))))))
