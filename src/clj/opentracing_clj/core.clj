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

(def ^:dynamic ^Tracer *tracer*
  "An Tracer object representing the standard tracer for trace operations.

  Defaults to the value returned by GlobalTracer.get().  Can be set with init!."
  (GlobalTracer/get))

;; Span
;; ----

(defn context
  "Returns the associated SpanContext of a span."
  [^Span span]
  (.context span))

(defn finish
  "Sets the end timestamp to now and records the span.  Can also supply an explicit timestamp in microseconds."
  ([^Span span]
   (.finish span))
  ([^Span span ^long timestamp]
   (.finish span timestamp)))

(defn get-baggage-item
  "Returns the value of the baggage item identified by the given key, or
  nil if no such item could be found."
  [^Span span ^String key]
  (.getBaggageItem span key))

(defn log-event
  "Logs a string event on the span.  Can also supply an explicit timestamp in microseconds.

  Returns the span for chaining."
  ([^Span span ^String event]
   (.log span event))
  ([^Span span ^String event ^Long timestamp]
   (.log span timestamp event)))

(defn log-map
  "Logs a map on the span.  Can also supply an explicit timestamp in microseconds.

  Note: Will automatically convert keys into strings."
  ([^Span span map]
   (.log span ^java.util.Map (walk/stringify-keys map)))
  ([^Span span map ^Long timestamp]
   (.log span timestamp ^java.util.Map (walk/stringify-keys map))))

(defn set-baggage-item
  "Sets a baggage item on the Span as a key/value pair."
  [^Span span ^String key ^String val]
  (.setBaggageItem span key val))

(defn set-baggage-items
  "Sets baggage items on the Span using key/value pairs of a map.

  Note: Will automatically convert keys into strings."
  [^Span span map]
  (when (map? map)
    (let [sm (walk/stringify-keys map)]
      (doseq [[k v] sm]
        (set-baggage-item span k v))))
  span)

(defn set-operation-name
  "Sets the string name for the logical operation this span represents."
  [^Span span ^String name]
  (.setOperationName span name))

(defn set-tag
  "Sets a key/value tag on the Span."
  [^Span span ^String key value]
  (cond
    (instance? Boolean value) (.setTag span key ^Boolean value)
    (instance? Number value)  (.setTag span key ^Number value)
    :else                     (.setTag span key ^String (str value))))

(defn set-tags
  "Sets tags on the Span using key/value pairs of a map.

  Note: Will automatically convert keys into strings."
  [^Span s m]
  (when (map? m)
    (let [sm (walk/stringify-keys m)]
      (doseq [[k v] sm]
        (set-tag s k v))))
  s)

(defn active-span
  "Returns the current active span."
  []
  (when *tracer*
    (.activeSpan *tracer*)))

(defmacro with-span
  "bindings => [name data]

  Evaluates body in the scope of a generated span."
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
  "Returns a map of the SpanContext in the specified carrier format for
  the purpose of propagation across process boundaries.

  Defaults to active span context."
  ([format]
   (when-let [s (active-span)]
     (inject (context s) format)))
  ([^SpanContext ctx format]
   (when-let [t *tracer*]
     (let [hm (java.util.HashMap.)
           tm (TextMapInjectAdapter. hm)]
       (.inject t ctx (get formats format) tm)
       (into {} hm)))))

(defn extract
  "Extract a SpanContext from a carrier of a given type, presumably
  after propagation across a process boundary."
  [^java.util.Map carrier format]
  (when-let [t *tracer*]
    (let [hm (java.util.HashMap. carrier)
          tm (TextMapExtractAdapter. hm)]
      (.extract t (get formats format) tm))))

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
  "Middleware for instrumenting a ring handler with tracing.  Handles
  HTTP header context propagation.

  Adds a ::span field to the ring request for use downstream.

  op-name-fn       = (f ring-request)  => op-name
  request-tags-fn  = (f ring-request)  => request-tags
  response-tags-fn = (f ring-response) => response-tags"
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

;; Instrumentation
;; ---------------

(defonce ^:private traced-vars (atom {}))

(defn ^:private fn-var?
  [v]
  (let [f @v]
    (or (and (-> v meta contains? :arglists)
             (-> v meta :macro not))
        (fn? f)
        (instance? clojure.lang.MultiFn f))))

(defn ^:private traced-fn
  [f m]
  (fn [& args]
    (let [{:keys [name tags]} m]
      (with-span [_ {:name name
                     :tags tags}]
        (.applyTo ^clojure.lang.IFn f args)))))

(defn ^:private ->sym
  "Returns a symbol from a symbol or var"
  [x]
  (if (var? x)
    (let [^clojure.lang.Var v x]
      (symbol (str (.name (.ns v)))
              (str (.sym v))))
    x))

(defn default-var-span-fn
  "Builds a span binding map for use with with-span given a var v."
  [v]
  (let [m (meta v)]
    {:name (str (:ns m) "/" (:name m))}))

(defn trace!
  "Instruments the function named by sym with tracing logic.

  Can be supplied with a var-span-fn for customizing the spans built.
  var-span-fn takes in the var of the traced symbol."
  ([sym]
   (trace! sym default-var-span-fn))
  ([sym var-span-fn]
   (when-let [v (resolve sym)]
     (when (fn-var? v)
       (let [{:keys [raw wrapped]} (get @traced-vars v)
             current               @v
             to-wrap               (if (= wrapped current) raw current)
             span                  (var-span-fn v)
             traced                (traced-fn to-wrap span)]
         (alter-var-root v (constantly traced))
         (swap! traced-vars assoc v {:raw to-wrap :wrapped traced})
         (->sym v))))))

(defn untrace!
  "Removes trace instrumentation from the function named by sym."
  [sym]
  (when-let [v (resolve sym)]
    (when-let [{:keys [raw wrapped]} (get @traced-vars v)]
      (swap! traced-vars dissoc v)
      (let [current @v]
        (when (= wrapped current)
          (alter-var-root v (constantly raw))
          (->sym v))))))


;; Init
;; ----

(defn init!
  "Initializes the root binding of *tracer* to the given tracer."
  ([^Tracer tracer]
   (alter-var-root #'*tracer* (constantly tracer))))
