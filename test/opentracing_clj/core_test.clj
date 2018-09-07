(ns opentracing-clj.core-test
  (:require [clojure.test :refer :all]
            [clojure.walk :as walk]
            [opentracing-clj.core :refer :all])
  (:import [io.opentracing.mock MockSpan MockTracer]))

(defn with-mock-tracer
  [f]
  (binding [*tracer* (new MockTracer)]
    (f)))

(use-fixtures :each with-mock-tracer)

(deftest with-span-test
  (testing "with-span"
    (testing "set span name"
      (let [span-name "test-1"]
        (with-span [s {:name span-name}]
          (+ 1 1))
        (let [spans (.finishedSpans *tracer*)]
          (is (= (.operationName (nth spans 0)) span-name)))))

    (testing "set span tags"
      (let [span-name "test-2"
            span-tags {:component "test-component"}]
        (with-span [s {:name span-name
                       :tags span-tags}]
          (+ 1 1))
        (let [spans (.finishedSpans *tracer*)]
          (is (= (.tags (nth spans 1))
                 (walk/stringify-keys span-tags))))))

    (testing "tracer span record count"
      (is (= (count (.finishedSpans *tracer*)) 2)))))
