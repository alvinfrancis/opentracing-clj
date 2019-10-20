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
    (let [outer-span (.. *tracer* (buildSpan "outer") (start))]
      (try
        (with-open [outer-scope (.. *tracer*
                                    (scopeManager)
                                    (activate outer-span))]
          (let [[ref-type ctx] ["test-type" (.context outer-span)]]
            (is (= [ref-type ctx]
                   (let [sb   (.buildSpan *tracer* "test")
                         _    (add-reference sb ref-type ctx)
                         span (.start sb)]
                     (try
                       (with-open [scope (.. *tracer*
                                             (scopeManager)
                                             (activate span))]
                         (let [ref (first (.references span))]
                           [(.getReferenceType ref) (.getContext ref)]))
                       (finally
                         (.finish span))))))))
        (finally
          (.finish outer-span))))))

(deftest ignore-active-test
  (testing "ignore-active"
    (let [outer-span (.. *tracer* (buildSpan "outer") (start))]
      (try
        (with-open [outer-scope (.. *tracer*
                                    (scopeManager)
                                    (activate outer-span))]
          (is (= 0 (let [sb   (.. *tracer* (buildSpan "test"))
                         _    (ignore-active sb)
                         span (.start sb)]
                     (try
                       (with-open [scope (.. *tracer*
                                             (scopeManager)
                                             (activate span))]
                         (count (.references span)))
                       (finally
                         (.finish span)))))))
        (finally
          (.finish outer-span))))))


(deftest add-tag-test
  (testing "add-tag"
    (let [sb (.. *tracer* (buildSpan "test"))]
      (is (thrown? ClassCastException (add-tag sb :non-string-key "value")))

      (add-tag sb "string-key" "string-val")
      (add-tag sb "boolean" true)
      (add-tag sb "number" 1)
      (add-tag sb "object" {:some :map})
      (let [span (.start sb)]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (is (= {"string-key"  "string-val"
                    "boolean"     true
                    "number"      1
                    "object"      "{:some :map}"}
                   (.tags span))))
          (finally
            (.finish span)))))))

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

      (is (= out-tags (let [sb   (.. *tracer* (buildSpan "test"))
                            _    (add-tags sb in-tags)
                            span (.start sb)]
                        (try
                          (with-open [scope (.. *tracer*
                                                (scopeManager)
                                                (activate span))]
                            (.tags span))
                          (finally
                            (.finish span))))))

      (let [to-merge-tags {"string-key2" "string-val2"
                           "boolean"     false}]
        (is (= (merge out-tags to-merge-tags)
               (let [sb   (.. *tracer* (buildSpan "test"))
                     _    (do
                            (add-tags sb in-tags)
                            (add-tags sb to-merge-tags))
                     span (.start sb)]
                 (try
                   (with-open [scope (.. *tracer*
                                         (scopeManager)
                                         (activate span))]
                     (.tags span))
                   (finally
                     (.finish span)))))))

      (is (= {} (let [sb   (.. *tracer* (buildSpan "test"))
                      _    (add-tags sb nil)
                      span (.start sb)]
                  (try
                    (with-open [scope (.. *tracer*
                                          (scopeManager)
                                          (activate span))]
                      (.tags span))
                    (finally
                      (.finish span)))))))))

(deftest child-of-test
  (testing "child-of"
    (let [outer-span (.. *tracer* (buildSpan "outer") (start))]
      (try
        (with-open [outer (.. *tracer*
                              (scopeManager)
                              (activate outer-span))]
          (let [ctx (.context outer-span)]
            (is (= ["child_of" ctx]
                   (let [sb   (.. *tracer* (buildSpan "test"))
                         _    (child-of sb outer-span)
                         span (.start sb)]
                     (try
                       (with-open [scope (.. *tracer*
                                             (scopeManager)
                                             (activate span))]
                         (let [ref (first (.references span))]
                           [(.getReferenceType ref) (.getContext ref)]))
                       (finally
                         (.finish span))))))

            (is (= ["child_of" ctx]
                   (let [sb   (.. *tracer* (buildSpan "test"))
                         _    (child-of sb ctx)
                         span (.start sb)]
                     (try
                       (with-open [scope (.. *tracer*
                                             (scopeManager)
                                             (activate span))]
                         (let [ref (first (.references span))]
                           [(.getReferenceType ref) (.getContext ref)]))
                       (finally
                         (.finish span))))))))
        (finally
          (.finish outer-span))))))

(deftest with-start-timestamp-test
  (testing "with-start-timestamp"
    (let [ms   10
          sb   (.. *tracer* (buildSpan "test"))
          _    (with-start-timestamp sb ms)
          span (.start sb)]
      (try
        (with-open [scope (.. *tracer*
                              (scopeManager)
                              (activate span))]
          (is (= ms (.startMicros span))))
        (finally
          (.finish span))))))

(deftest build-span-test
  (testing "build-span"
    (let [op-name "test"]
      (is (instance? Tracer$SpanBuilder (build-span *tracer* op-name)))
      (is (= op-name (let [span (.start (build-span *tracer* op-name))]
                       (try
                         (with-open [scope (.. *tracer*
                                               (scopeManager)
                                               (activate span))]
                           (.operationName span))
                         (finally
                           (.finish span)))))))))
