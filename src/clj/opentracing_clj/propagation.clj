(ns opentracing-clj.propagation
  "Functions for cross-process propagation of span contexts."
  (:require [opentracing-clj.core :as tracing])
  (:import (io.opentracing SpanContext)
           (io.opentracing.propagation Format$Builtin
                                       TextMapAdapter)))

(def formats {:http Format$Builtin/HTTP_HEADERS
              :text Format$Builtin/TEXT_MAP})

(defn inject
  "Returns a map of the SpanContext in the specified carrier format for
  the purpose of propagation across process boundaries.

  Defaults to active span context."
  ([format]
   (when-let [s (tracing/active-span)]
     (inject (tracing/context s) format)))
  ([^SpanContext ctx format]
   (when-let [t tracing/*tracer*]
     (let [hm (java.util.HashMap.)
           tm (TextMapAdapter. hm)]
       (.inject t ctx (get formats format) tm)
       (into {} hm)))))

(defn extract
  "Extract a SpanContext from a carrier of a given type, presumably
  after propagation across a process boundary."
  [^java.util.Map carrier format]
  (when-let [t tracing/*tracer*]
    (let [hm (java.util.HashMap. carrier)
          tm (TextMapAdapter. hm)]
      (.extract t (get formats format) tm))))
