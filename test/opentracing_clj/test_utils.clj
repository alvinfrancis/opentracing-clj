(ns opentracing-clj.test-utils
  (:require
   [opentracing-clj.core :as tracing])
  (:import
   [io.opentracing.mock MockTracer]))

(defn with-mock-tracer
  [f]
  (binding [tracing/*tracer* (new MockTracer)]
    (f)))
