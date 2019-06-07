(ns opentracing-clj.span-builder-test
  (:require
   [clojure.test :refer :all]
   [opentracing-clj.core :as tracing :refer [*tracer*]]
   [opentracing-clj.span-builder :refer :all]
   [opentracing-clj.test-utils :as utils])
  (:import
   [io.opentracing Tracer$SpanBuilder]
   [io.opentracing.mock MockTracer]))

(use-fixtures :each utils/with-mock-tracer)

(deftest add-reference-test
  (testing "add-reference"
    (with-open [outer (-> *tracer* (.buildSpan "outer") (.startActive true))]
      (let [[ref-type ctx] ["test-type" (.context (.span outer))]]
        (is (= [ref-type ctx]
               (let [sb (-> *tracer* (.buildSpan "test"))]
                 (add-reference sb ref-type ctx)
                 (with-open [scope (.startActive sb true)]
                   (let [ref (first (.references (.span scope)))]
                     [(.getReferenceType ref) (.getContext ref)])))))))))

(deftest ignore-active-test
  (testing "ignore-active"
    (with-open [outer (-> *tracer* (.buildSpan "outer") (.startActive true))]
      (is (= 0 (let [sb (-> *tracer* (.buildSpan "test"))]
                 (ignore-active sb)
                 (with-open [scope (.startActive sb true)]
                   (count (.references (.span scope))))))))))


(deftest add-tag-test
  (testing "add-tag"
    (let [sb (-> *tracer* (.buildSpan "test"))]
      (is (thrown? ClassCastException (add-tag sb :non-string-key "value")))
      (add-tag sb "string-key" "string-val")
      (add-tag sb "boolean" true)
      (add-tag sb "number" 1)
      (add-tag sb "object" {:some :map})
      (with-open [scope (.startActive sb true)]
        (is (= {"string-key"  "string-val"
                "boolean"     true
                "number"      1
                "object"      "{:some :map}"}
               (.tags (.span scope))))))))

(deftest add-tags-test
  (testing "add-tags"
    (let [in-tags  {"string-key" "string-val"
                    :keyword-key :key-val
                    "boolean"    true
                    "number"     1
                    "object"     {:some :map}}
          out-tags {"string-key"  "string-val"
                    "keyword-key" ":key-val"
                    "boolean"     true
                    "number"      1
                    "object"      "{:some :map}"}]

      (is (= out-tags (let [sb (-> *tracer* (.buildSpan "test"))]
                        (add-tags sb in-tags)
                        (with-open [scope (.startActive sb true)]
                          (.tags (.span scope))))))

      (let [to-merge-tags {"string-key2" "string-val2"
                           "boolean"     false}]
        (is (= (merge out-tags to-merge-tags)
               (let [sb (-> *tracer* (.buildSpan "test"))]
                 (add-tags sb in-tags)
                 (add-tags sb to-merge-tags)
                 (with-open [scope (.startActive sb true)]
                   (.tags (.span scope)))))))

      (is (= {} (let [sb (-> *tracer* (.buildSpan "test"))]
                  (add-tags sb nil)
                  (with-open [scope (.startActive sb true)]
                    (.tags (.span scope)))))))))

(deftest child-of-test
  (testing "child-of"
    (with-open [outer (-> *tracer* (.buildSpan "outer") (.startActive true))]
      (let [ctx (.context (.span outer))]
        (is (= ["child_of" ctx]
               (let [sb (-> *tracer* (.buildSpan "test"))]
                 (child-of sb (.span outer))
                 (with-open [scope (.startActive sb true)]
                   (let [ref (first (.references (.span scope)))]
                     [(.getReferenceType ref) (.getContext ref)])))))

        (is (= ["child_of" ctx]
               (let [sb (-> *tracer* (.buildSpan "test"))]
                 (child-of sb ctx)
                 (with-open [scope (.startActive sb true)]
                   (let [ref (first (.references (.span scope)))]
                     [(.getReferenceType ref) (.getContext ref)])))))))))

(deftest with-start-timestamp-test
  (testing "with-start-timestamp"
    (let [ms 10
          sb (-> *tracer* (.buildSpan "test"))]
      (with-start-timestamp sb ms)
      (with-open [scope (.startActive sb true)]
        (is (= ms (.startMicros (.span scope))))))))

(deftest start-test
  (testing "deprecated start"
    (is (= 0 (do (with-open [scope (-> (-> *tracer* (.buildSpan "test")) (start false))])
                 (count (.finishedSpans *tracer*)))))
    (.reset *tracer*)
    (is (= 1 (do (with-open [scope (-> (-> *tracer* (.buildSpan "test")) (start true))])
                 (count (.finishedSpans *tracer*)))))))

(deftest build-span-test
  (testing "build-span"
    (let [op-name "test"]
      (is (instance? Tracer$SpanBuilder (-> *tracer* (build-span op-name))))
      (is (= op-name (with-open [scope (-> *tracer* (build-span op-name) (.startActive true))]
                       (.operationName (.span scope))))))))
