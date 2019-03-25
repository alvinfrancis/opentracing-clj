(ns opentracing-clj.core-test
  (:require [clojure.test :refer :all]
            [clojure.walk :as walk]
            [opentracing-clj.core :refer :all]
            [opentracing-clj.test-utils :as utils])
  (:import [io.opentracing.mock MockSpan MockTracer]))

(use-fixtures :each utils/with-mock-tracer)

(deftest active-span-test
  (testing "active span"
    (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
      (is (= (active-span) (.span scope))))
    (is (nil? (active-span)))))

(deftest context-test
  (testing "context"
    (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
      (testing "active span"
        (is (= (context) (.context (.span scope))))
        (is (= (context (.span scope))
               (.context (.span scope))))))
    (testing "no active span"
      (is (nil? (context))))))

(deftest finish-test
  (testing "finish"
    (let [scope-1 (-> *tracer* (.buildSpan "span-1") (.startActive true))
          scope-2 (-> *tracer* (.buildSpan "span-2") (.startActive true))
          scope-3 (-> *tracer* (.buildSpan "span-3") (.startActive true))]
      (testing "active span"
        (do
          (finish)
          (let [spans (.finishedSpans *tracer*)]
            (is (= 1 (count spans)))
            (is (= "span-3" (.operationName (nth spans 0))))))

        (do
          (finish (.span scope-2))
          (let [spans (.finishedSpans *tracer*)]
            (is (= 2 (count spans)))
            (is (= "span-2" (.operationName (nth spans 1)))))))

      (testing "timestamp"
        (.reset *tracer*)
        (finish (.span scope-1) 10)
        (let [span (first (.finishedSpans *tracer*))]
          (is (= "span-1" (.operationName span)))
          (is (= 10 (.finishMicros span))))))

    (testing "no active span"
      (is (thrown? IllegalStateException (finish))))))

(deftest log-test
  (testing "log"
    (testing "active span"
      (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
        (log "test")
        (log {:key "value"})
        (log (.span scope) 1))
      (let [span (first (.finishedSpans *tracer*))]
        (is (< 0 (count (.logEntries span))))
        (let [entry (nth (.logEntries span) 0)]
          (is (= "test" (get (.fields entry) "event"))))
        (let [entry (nth (.logEntries span) 1)]
          (is (= "value" (get (.fields entry) "key"))))
        (let [entry (nth (.logEntries span) 2)]
          (is (= "1" (get (.fields entry) "event"))))))

    (testing "with timestamp"
      (.reset *tracer*)
      (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
        (let [span (.span scope)]
          (log span :not-string 10)))
      (let [span (first (.finishedSpans *tracer*))]
        (is (< 0 (count (.logEntries span))))
        (let [entry (nth (.logEntries span) 0)]
          (is (= ":not-string" (get (.fields entry) "event"))))))

    (testing "no active span"
      (is (nil? (log "test"))))))

(deftest baggage-item-test
  (testing "baggage-item"
    (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
      (.setBaggageItem (.span scope) "key" "value")
      (testing "active span"
        (is (= "value" (baggage-item "key")))
        (is (= "value" (baggage-item (.span scope) "key")))
        (is (nil? (baggage-item "unknown")))
        (is (nil? (baggage-item (.span scope) "unknown")))))

    (testing "no active span"
      (is (nil? (baggage-item "key"))))))

(deftest set-baggage-item-test
  (testing "baggage-item"
    (testing "active span"
      (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
        (is (= "active" (do (set-baggage-item "active" "active")
                            (.getBaggageItem (.span scope) "active"))))
        (is (= "override" (do (set-baggage-item "active" "override")
                              (.getBaggageItem (.span scope) "active"))))
        (is (= "passed" (do (set-baggage-item (.span scope) "passed" "passed")
                            (.getBaggageItem (.span scope) "passed"))))))

    (testing "no active span"
      (is (nil? (set-baggage-item "key" "value"))))))

(deftest set-baggage-items-test
  (testing "baggage-item"
    (testing "active span"
      (let [in-baggage {"string-key" "string-val"
                        :keyword-key :key-val
                        "number"     1
                        "object"     {:some :map}}]
        (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
          (set-baggage-items in-baggage)
          (is (= "string-val"   (.getBaggageItem (.span scope) "string-key")))
          (is (= ":key-val"     (.getBaggageItem (.span scope) "keyword-key")))
          (is (= "1"            (.getBaggageItem (.span scope) "number")))
          (is (= "{:some :map}" (.getBaggageItem (.span scope) "object"))))
        (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
          (set-baggage-items (.span scope) in-baggage)
          (is (= "string-val"   (.getBaggageItem (.span scope) "string-key")))
          (is (= ":key-val"     (.getBaggageItem (.span scope) "keyword-key")))
          (is (= "1"            (.getBaggageItem (.span scope) "number")))
          (is (= "{:some :map}" (.getBaggageItem (.span scope) "object"))))))

    (testing "no active span"
      (is (nil? (set-baggage-items {:key "value"}))))))

(deftest set-operation-name-test
  (testing "set-operation-name"
    (testing "active span"
      (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
        (is (= "active" (do (set-operation-name "active")
                            (.operationName (.span scope)))))
        (is (= "passed" (do (set-operation-name (.span scope) "passed")
                            (.operationName (.span scope)))))))

    (testing "no active span"
      (is (nil? (set-operation-name "unknown"))))))

(deftest set-tag-test
  (testing "set-tag"
    (testing "active span"
      (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
        (is (= "active" (do (set-tag "key" "active")
                            (get (.tags (.span scope)) "key"))))
        (is (= "override" (do (set-tag "key" "active")
                              (set-tag "key" "override")
                              (get (.tags (.span scope)) "key"))))
        (is (= "passed" (do (set-tag "key" "passed")
                            (get (.tags (.span scope)) "key"))))
        (is (= true (do (set-tag "boolean" true)
                        (get (.tags (.span scope)) "boolean"))))
        (is (= 1 (do (set-tag "number" 1)
                     (get (.tags (.span scope)) "number"))))
        (is (= "{:some :map}" (do (set-tag "map" {:some :map})
                                  (get (.tags (.span scope)) "map"))))
        (is (thrown? ClassCastException (set-tag :not-string-key "key-val")))))

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
        (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
          (is (= out-tags (do (set-tags in-tags)
                              (.tags (.span scope))))))

        (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
          (is (= out-tags (do (set-tags (.span scope) in-tags)
                              (.tags (.span scope))))))

        (with-open [scope (-> *tracer* (.buildSpan "test") (.startActive true))]
          (is (= {} (do (set-tags nil)
                        (.tags (.span scope)))))
          (is (= {"a" "base" "b" "override" "c" "new"}
                 (do (set-tags {:a "base" :b "base"})
                     (set-tags {:a "base" :b "override"})
                     (set-tags {:c "new"})
                     (.tags (.span scope))))))))

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


    (testing "existing span"
      (.reset *tracer*)
      (let [s1        (-> *tracer* (.buildSpan "test") (.start))
            process-1 (future
                        (with-span [t {:from s1}]
                          (is (= s1 (.activeSpan *tracer*))))
                        (is (= 1 (count (.finishedSpans *tracer*)))))
            s2        (-> *tracer* (.buildSpan "test") (.start))
            process-2 (future
                        (with-span [t {:from    s2
                                       :finish? false}]
                          (is (= s2 (.activeSpan *tracer*))))
                        (is (= 1 (count (.finishedSpans *tracer*)))))]
        @process-1
        @process-2))

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
                       (is (= 1 (count (.finishedSpans *tracer*)))))]
        process))))
