(ns opentracing-clj.core-test
  (:require [clojure.test :refer :all]
            [clojure.walk :as walk]
            [opentracing-clj.core :refer :all]
            [opentracing-clj.test-utils :as utils])
  (:import [io.opentracing References]
           [io.opentracing.mock MockSpan MockTracer]))

(use-fixtures :each utils/with-mock-tracer)

(deftest active-span-test
  (testing "active span"
    (let [span (.. *tracer* (buildSpan "test") (start))]
      (try
        (with-open [scope (.. *tracer*
                              (scopeManager)
                              (activate span))]
          (is (= (active-span) span)))
        (finally
          (.finish span))))
    (is (nil? (active-span)))))

(deftest context-test
  (testing "context"
    (let [span (.. *tracer* (buildSpan "test") (start))]
      (try
        (with-open [scope (.. *tracer*
                              (scopeManager)
                              (activate span))]
          (testing "active span"
            (is (= (context) (.context span)))
            (is (= (context span) (.context span)))))
        (finally
          (.finish span))))
    (testing "no active span"
      (is (nil? (context))))))

(deftest finish-test
  (testing "finish"
    (let [span-1  (.. *tracer* (buildSpan "span-1") (start))
          scope-1 (.. *tracer* (scopeManager) (activate span-1))
          span-2  (.. *tracer* (buildSpan "span-2") (start))
          scope-2 (.. *tracer* (scopeManager) (activate span-2))
          span-3  (.. *tracer* (buildSpan "span-3") (start))
          scope-3 (.. *tracer* (scopeManager) (activate span-3))]
      (testing "active span"
        (do
          (finish)
          (let [spans (.finishedSpans *tracer*)]
            (is (= 1 (count spans)))
            (is (= "span-3" (.operationName (nth spans 0))))))

        (do
          (finish span-2)
          (let [spans (.finishedSpans *tracer*)]
            (is (= 2 (count spans)))
            (is (= "span-2" (.operationName (nth spans 1)))))))

      (testing "timestamp"
        (.reset *tracer*)
        (finish span-1 10)
        (let [span (first (.finishedSpans *tracer*))]
          (is (= "span-1" (.operationName span)))
          (is (= 10 (.finishMicros span))))))

    (testing "no active span"
      (is (thrown? IllegalStateException (finish))))))

(deftest log-test
  (testing "log"
    (testing "active span"
      (let [span (.. *tracer* (buildSpan "test") (start))]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (log "string-value")
            (log :non-string-value)
            (log {:keyword-key :non-string-value
                  "string-key" "string-value"})
            (log span "explicit set span"))
          (finally
            (.finish span))))

      (let [span (first (.finishedSpans *tracer*))
            logs (.logEntries span)]
        (is (= 4 (count logs)))
        (is (= "string-value"      (-> logs (nth 0) (.fields) (get "event"))))
        (is (= ":non-string-value" (-> logs (nth 1) (.fields) (get "event"))))
        (is (= :non-string-value   (-> logs (nth 2) (.fields) (get "keyword-key"))))
        (is (= "string-value"      (-> logs (nth 2) (.fields) (get "string-key"))))
        (is (= "explicit set span" (-> logs (nth 3) (.fields) (get "event"))))))

    (testing "with timestamp"
      (.reset *tracer*)
      (let [span (.. *tracer* (buildSpan "test") (start))]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (log span "string-value" 10)
            (log span :non-string-value 10)
            (log span {:keyword-key :non-string-value
                       "string-key" "string-value"} 10))
          (finally
            (.finish span))))

      (let [span (first (.finishedSpans *tracer*))
            logs (.logEntries span)]
        (is (< 0 (count logs)))
        (is (= "string-value"      (-> logs (nth 0) (.fields) (get "event"))))
        (is (= ":non-string-value" (-> logs (nth 1) (.fields) (get "event"))))
        (is (= :non-string-value   (-> logs (nth 2) (.fields) (get "keyword-key"))))
        (is (= "string-value"      (-> logs (nth 2) (.fields) (get "string-key"))))))

    (testing "no active span"
      (is (nil? (log "test"))))))

(deftest baggage-item-test
  (testing "baggage-item"
    (let [span (.. *tracer* (buildSpan "test") (start))]
      (try
        (with-open [scope (.. *tracer* (scopeManager) (activate span))]
          (.setBaggageItem span "key" "value")
          (testing "active span"
            (is (= "value" (baggage-item "key")))
            (is (= "value" (baggage-item span "key")))
            (is (nil? (baggage-item "unknown")))
            (is (nil? (baggage-item span "unknown")))))
        (finally
          (.finish span))))

    (testing "no active span"
      (is (nil? (baggage-item "key"))))))

(deftest set-baggage-item-test
  (testing "baggage-item"
    (testing "active span"
      (let [span (.. *tracer* (buildSpan "test") (start))]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (is (= "active" (do (set-baggage-item "active" "active")
                                (.getBaggageItem span "active"))))
            (is (= "override" (do (set-baggage-item "active" "override")
                                  (.getBaggageItem span "active"))))
            (is (= "passed" (do (set-baggage-item span "passed" "passed")
                                (.getBaggageItem span "passed")))))
          (finally
            (.finish span)))))

    (testing "no active span"
      (is (nil? (set-baggage-item "key" "value"))))))

(deftest set-baggage-items-test
  (testing "baggage-item"
    (testing "active span"
      (let [in-baggage {"string-key" "string-val"
                        :keyword-key :key-val
                        "number"     1
                        "object"     {:some :map}}]
        (let [span (.. *tracer*
                       (buildSpan "test")
                       (start))]
          (try
            (with-open [scope (.. *tracer*
                                  (scopeManager)
                                  (activate span))]
              (set-baggage-items in-baggage)
              (is (= "string-val"   (.getBaggageItem span "string-key")))
              (is (= ":key-val"     (.getBaggageItem span "keyword-key")))
              (is (= "1"            (.getBaggageItem span "number")))
              (is (= "{:some :map}" (.getBaggageItem span "object"))))
            (finally
              (.finish span))))
        (let [span (.. *tracer*
                       (buildSpan "test")
                       (start))]
          (try
            (with-open [scope (.. *tracer*
                                  (scopeManager)
                                  (activate span))]
              (set-baggage-items span in-baggage)
              (is (= "string-val"   (.getBaggageItem span "string-key")))
              (is (= ":key-val"     (.getBaggageItem span "keyword-key")))
              (is (= "1"            (.getBaggageItem span "number")))
              (is (= "{:some :map}" (.getBaggageItem span "object"))))
            (finally
              (.finish span))))))

    (testing "no active span"
      (is (nil? (set-baggage-items {:key "value"}))))))

(deftest set-operation-name-test
  (testing "set-operation-name"
    (testing "active span"
      (let [span (.. *tracer*
                     (buildSpan "test")
                     (start))]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (is (= "active" (do (set-operation-name "active")
                                (.operationName span))))
            (is (= "passed" (do (set-operation-name span "passed")
                                (.operationName span)))))
          (finally
            (.finish span)))))

    (testing "no active span"
      (is (nil? (set-operation-name "unknown"))))))

(deftest set-tag-test
  (testing "set-tag"
    (testing "active span"
      (let [span (.. *tracer* (buildSpan "test") (start))]
        (try
          (with-open [scope (.. *tracer* (scopeManager) (activate span))]
            (is (= "active" (do (set-tag "key" "active")
                                (get (.tags span) "key"))))
            (is (= "override" (do (set-tag "key" "active")
                                  (set-tag "key" "override")
                                  (get (.tags span) "key"))))
            (is (= "passed" (do (set-tag "key" "passed")
                                (get (.tags span) "key"))))
            (is (= true (do (set-tag "boolean" true)
                            (get (.tags span) "boolean"))))
            (is (= 1 (do (set-tag "number" 1)
                         (get (.tags span) "number"))))
            (is (= "{:some :map}" (do (set-tag "map" {:some :map})
                                      (get (.tags span) "map"))))
            (is (thrown? ClassCastException (set-tag :not-string-key "key-val"))))
          (finally
            (.finish span)))))

    (testing "no active span"
      (is (nil? (set-tag "unknown" "unknown"))))))

(deftest set-tags-test
  (testing "set-tags"
    (testing "active span"
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
        (let [span (.. *tracer* (buildSpan "test") (start))]
          (try
            (with-open [scope (.. *tracer* (scopeManager) (activate span))]
              (is (= out-tags (do (set-tags in-tags)
                                  (.tags span)))))
            (finally
              (.finish span))))

        (let [span (.. *tracer* (buildSpan "test") (start))]
          (try
            (with-open [scope (.. *tracer* (scopeManager) (activate span))]
              (is (= out-tags (do (set-tags span in-tags)
                                  (.tags span)))))
            (finally
              (.finish span))))

        (let [span (.. *tracer* (buildSpan "test") (start))]
          (try
            (with-open [scope (.. *tracer* (scopeManager) (activate span))]
              (is (= {} (do (set-tags nil)
                            (.tags span))))
              (is (= {"a" "base" "b" "override" "c" "new"}
                     (do (set-tags {:a "base" :b "base"})
                         (set-tags {:a "base" :b "override"})
                         (set-tags {:c "new"})
                         (.tags span)))))
            (finally
              (.finish span))))))

    (testing "no active span"
      (is (nil? (set-tags {:key "value"}))))))

(deftest with-span-test
  (testing "with-span"
    (testing "set span name"
      (let [span-name "test-1"]
        (with-span [s {:name span-name}]
          (+ 1 1))
        (is (= span-name (.operationName (first (.finishedSpans *tracer*)))))
        (is (= 1 (count (.finishedSpans *tracer*))))))

    (testing "set span tags"
      (.reset *tracer*)
      (let [span-name "test-2"
            span-tags {:component "test-component"}]
        (with-span [s {:name span-name
                       :tags span-tags}]
          (+ 1 1))
        (is (= (walk/stringify-keys span-tags)
               (.tags (first (.finishedSpans *tracer*)))))))

    (testing "set timestamp"
      (.reset *tracer*)
      (let [span-name    "test-1"
            start-micros 10000000]
        (with-span [s {:name            span-name
                       :start-timestamp start-micros}]
          (is (= start-micros (.startMicros s))))))

    (testing "existing span"
      (.reset *tracer*)
      (let [s1        (-> *tracer* (.buildSpan "test1") (.start))
            process-1 (future
                        (with-span [t {:from s1}]
                          (is (= s1 (.activeSpan *tracer*)))))
            s2        (-> *tracer* (.buildSpan "test2") (.start))
            process-2 (future
                        (with-span [t {:from    s2
                                       :finish? false}]
                          (is (= s2 (.activeSpan *tracer*)))))]
        @process-1
        @process-2
        (is (= 1 (count (.finishedSpans *tracer*))))))

    (testing "failed spec"
      (.reset *tracer*)
      (is (thrown? Exception (with-span [s {:unrecognized-keyword "test"}])))
      (try
        (with-span [s {:unrecognized-keyword "test"}])
        (catch Exception e
          (let [error (Throwable->map e)]
            (is (= "with-span binding failed to conform to :opentracing/span-init"
                   (:cause error)))))))

    (testing "ambiguous spec"
      (.reset *tracer*)
      (let [existing (-> *tracer* (.buildSpan "test") (.start))
            process  (future
                       ;; an init spec conforming to both the
                       ;; existing span spec and the new span spec
                       ;; should choose to use the existing span spec
                       (with-span [t {:name "new"
                                      :from existing}]
                         (is (= existing (.activeSpan *tracer*))))
                       )]
        @process
        (is (= 1 (count (.finishedSpans *tracer*))))))

    (testing "ignores active"
      (.reset *tracer*)
      (with-span [outer {:name "outer"}]
        (is (= 0 (with-span [inner {:name           "inner"
                                    :ignore-active? true}]
                   (count (.references inner)))))))

    (testing "exception in span"
      (.reset *tracer*)
      (let [s1        (-> *tracer* (.buildSpan "test1") (.start))
            process-1 (future
                        (try
                          (with-span [t {:from s1}]
                            (throw (Exception. "BOOM!")))
                          (catch Exception e)))
            s2        (-> *tracer* (.buildSpan "test2") (.start))
            process-2 (future
                        (try
                          (with-span [t {:from    s2
                                         :finish? false}]
                            (throw (Exception. "BOOM!")))
                          (catch Exception e)))]
        @process-1
        @process-2
        (is (= 1 (count (.finishedSpans *tracer*))))))

    (testing "set CHILD_OF reference"
      (.reset *tracer*)
      (testing "implicitly"
        (with-span [outer {:name "outer"}]
          (is (= [References/CHILD_OF (.context outer)]
                 (with-span [inner {:name "inner"}]
                   (let [ref (first (.references inner))]
                     [(.getReferenceType ref) (.getContext ref)]))))))

      (let [outer-span-1 (.. *tracer* (buildSpan "outer-scope-1") (start))]
        (try
          (with-open [outer-scope-1 (.. *tracer* (scopeManager) (activate outer-span-1))]
            (let [outer-span-2 (.. *tracer* (buildSpan "outer-scope-2") (start))]
              (try
                (with-open [outer-scope-2 (.. *tracer* (scopeManager) (activate outer-span-2))]
                  (let [ctx (.context outer-span-1)]
                    (testing "using span"
                      (is (= [References/CHILD_OF ctx]
                             (with-span [t {:name     "test"
                                            :child-of outer-span-1}]
                               (let [ref (first (.references t))]
                                 [(.getReferenceType ref) (.getContext ref)])))))
                    (testing "using span context"
                      (is (= [References/CHILD_OF ctx]
                             (with-span [t {:name     "test"
                                            :child-of (.context outer-span-1)}]
                               (let [ref (first (.references t))]
                                 [(.getReferenceType ref) (.getContext ref)])))))))
                (finally
                  (.finish outer-span-2)))))
          (finally
            (.finish outer-span-1)))))))
