(ns ^:no-doc opentracing-clj.span-builder
  (:require [clojure.walk :as walk])
  (:import (io.opentracing Span SpanContext Tracer Tracer$SpanBuilder)))

(defn add-reference
  [^Tracer$SpanBuilder sb ^String type ^SpanContext ctx]
  (.addReference sb type ctx))

(defn ignore-active
  [^Tracer$SpanBuilder sb]
  (.ignoreActiveSpan sb))

(defn add-tag
  [^Tracer$SpanBuilder sb ^String k v]
  (cond
    (instance? Boolean v) (.withTag sb k ^Boolean v)
    (instance? Number v)  (.withTag sb k ^Number v)
    :else                 (.withTag sb k ^String (str v))))

(defn add-tags
  [^Tracer$SpanBuilder sb m]
  (when (map? m)
    (doseq [[k v] m]
      (add-tag sb (if (keyword? k) (name k) (str k))
               v)))
  sb)

(defmulti child-of (fn [sb parent] (class parent)))

(defmethod child-of Span
  [^Tracer$SpanBuilder sb ^Span parent]
  (.asChildOf sb parent))

(defmethod child-of SpanContext
  [^Tracer$SpanBuilder sb ^SpanContext parent]
  (.asChildOf sb parent))

(defn with-start-timestamp
  [^Tracer$SpanBuilder sb timestamp]
  (.withStartTimestamp sb timestamp))

(defn start
  ([^Tracer$SpanBuilder sb finish-on-close?]
   (.startActive sb finish-on-close?)))

(defn build-span
  [^Tracer tracer ^String n]
  (.buildSpan tracer n))
