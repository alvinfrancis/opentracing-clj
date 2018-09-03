(ns opentracing-clj.spec
  (:require
   [clojure.spec.alpha :as s]
   [opentracing-clj.core :as tracing])
  (:import (io.opentracing Span SpanContext)))

(s/def ::microseconds-since-epoch int?)
(s/def ::span #(instance? Span %))
(s/def ::span-context #(instance? SpanContext %))

(s/def :opentracing.span/name string?)
(s/def :opentracing.span/tags map?)
(s/def :opentracing.span/ignore-active? boolean?)
(s/def :opentracing.span/timestamp ::microseconds-since-epoch)
(s/def :opentracing.span/child-of (s/or ::span ::span-context))
(s/def :opentracing.span/scoped? boolean?)
(s/def :opentracing.span/finish? boolean?)

(s/def ::span-init
  (s/keys :req-un [:opentracing.span/name]
          :opt-un [:opentracing.span/tags
                   :opentracing.span/ignore-active?
                   :opentracing.span/timestamp
                   :opentracing.span/child-of
                   :opentracing.span/scoped?
                   :opentracing.span/finish?]))

(s/def ::span-binding
  (s/spec
   (s/cat :span-sym simple-symbol?
          :span-spec ::span-init)))

(s/fdef tracing/with-span
  :args (s/cat :binding ::span-binding
               :body    (s/* any?)))
