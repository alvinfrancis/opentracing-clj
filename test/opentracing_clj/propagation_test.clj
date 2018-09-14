(ns opentracing-clj.propagation-test
  (:require
   [clojure.test :refer :all]
   [opentracing-clj.core :as tracing]
   [opentracing-clj.propagation :refer :all])
  (:import
   [io.opentracing.mock MockTracer]))

(defn with-mock-tracer
  [f]
  (binding [tracing/*tracer* (new MockTracer)]
    (f)))

(use-fixtures :each with-mock-tracer)

(deftest inject-extract-roundtrip-test
  (testing "inject extract roundtrip"
    (let [in-baggage {:baggage1 "1"
                      :baggage2 "baggage2"}]
      (tracing/with-span [s {:name "test"
                             :tags {:test.tag1 "tag1"}}]
        (tracing/set-baggage-items in-baggage)
        (let [ctx     (tracing/context)
              spanId  (.spanId ctx)
              traceId (.traceId ctx)
              baggage (.baggageItems ctx)]

          (let [extract-ctx (extract (inject :http) :http)]
            (is (= spanId (.spanId extract-ctx)))
            (is (= traceId (.traceId extract-ctx)))
            (doseq [[k v] baggage]
              (is (= v (.getBaggageItem extract-ctx k))))))))))
