(ns opentracing-clj.core
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [opentracing-clj.span-builder :as sb]
            [ring.util.request])
  (:import (io.opentracing Span SpanContext Tracer Scope Tracer$SpanBuilder)
           (io.opentracing.util GlobalTracer)
           (io.opentracing.propagation Format$Builtin
                                       TextMapExtractAdapter
                                       TextMapInjectAdapter)))

(def ^:dynamic ^Tracer *tracer* (GlobalTracer/get))

;; Span
;; ----

(defn context
  [^Span s]
  (.context s))

(defn finish
  ([^Span s]
   (.finish s))
  ([^Span s ^long m]
   (.finish s m)))

(defn get-baggage-item
  [^Span s ^String k]
  (.getBaggageItem s k))

(defn log-event
  ([^Span s ^String event]
   (.log s event))
  ([^Span s ^String event ^Long ts]
   (.log s ts event)))

(defn log-map
  ([^Span s m]
   (.log s ^java.util.Map (walk/stringify-keys m)))
  ([^Span s m ^Long ts]
   (.log s ts ^java.util.Map (walk/stringify-keys m))))

(defn set-baggage-item
  [^Span s ^String k ^String v]
  (.setBaggageItem s k v))

(defn set-operation-name
  [^Span s ^String op]
  (.setOperationName s op))

(defn set-tag
  [^Span s ^String k v]
  (cond
    (instance? Boolean v) (.setTag s k ^Boolean v)
    (instance? Number v)  (.setTag s k ^Number v)
    :else                 (.setTag s k ^String (str v))))

(defn set-tags
  [^Span s m]
  (when (map? m)
    (let [sm (walk/stringify-keys m)]
      (doseq [[k v] sm]
        (set-tag s k v))))
  s)

(defn active-span
  []
  (when *tracer*
    (.activeSpan *tracer*)))

(defmacro with-span
  [bindings & body]
  (let [s (bindings 0)
        m (bindings 1)]
    `(let [sb# (.buildSpan *tracer* ~(:name m))]
       (when-let [tags# ~(:tags m)]
         (sb/add-tags sb# tags#))
       (when ~(:ignore-active? m)
         (.ignoreActiveSpan sb#))
       (when-let [start-ts# ~(:start-timestamp m)]
         (.withStartTimestamp sb# start-ts#))
       (when-let [parent# ~(:child-of m)]
         (sb/child-of sb# parent#))
       (with-open [^Scope scope# (if (or (nil? ~(:scoped? m))
                                         ~(:scoped? m))
                                   (.startActive sb# (or (nil? ~(:finish? m))
                                                         ~(:finish? m)))
                                   (.start sb#))]
         (let [~s (.span scope#)]
           ~@body)))))

;; Propagation
;; -----------

(def formats {:http Format$Builtin/HTTP_HEADERS
              :text Format$Builtin/TEXT_MAP})

(defn inject
  [^SpanContext ctx fmt]
  (when-let [t *tracer*]
    (let [hm (java.util.HashMap.)
          tm (TextMapInjectAdapter. hm)]
      (.inject t ctx (get formats fmt) tm)
      (into {} hm))))

(defn extract
  [^java.util.Map header fmt]
  (when-let [t *tracer*]
    (let [hm (java.util.HashMap. header)
          tm (TextMapExtractAdapter. hm)]
      (.extract t (get formats fmt) tm))))

;; Utils
;; -----

(defn default-request-tags
  [{:keys [request-method] :as request}]
  {:http.method (string/upper-case (name request-method))
   :http.url    (ring.util.request/request-url request)})

(defn default-response-tags
  [{:keys [status] :as response}]
  {:http.status_code status})

(defn default-op-name
  [{:keys [request-method uri] :as request}]
  (str (string/upper-case (name request-method)) " " uri))

(defn wrap-opentracing
  ([handler]
   (wrap-opentracing handler default-op-name))
  ([handler op-name-fn]
   (wrap-opentracing handler op-name-fn default-request-tags))
  ([handler op-name-fn request-tags-fn]
   (wrap-opentracing handler op-name-fn request-tags-fn default-response-tags))
  ([handler op-name-fn request-tags-fn response-tags-fn]
   (fn [request]
     (let [ctx (extract (:headers request) :http)]
       (with-span [s {:name     (op-name-fn request)
                      :child-of ctx
                      :tags     (request-tags-fn request)}]
         (let [response (handler (assoc request ::span s))]
           (set-tags s (response-tags-fn response))
           response))))))

(defn init!
  ([^Tracer tracer]
   (alter-var-root #'*tracer* (constantly tracer))))
