(ns opentracing-clj.core
  "Functions for creating and manipulating spans for opentracing."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [opentracing-clj.span-builder :as sb]
   [ring.util.request])
  (:import (io.opentracing Span SpanContext Tracer Scope)
           (io.opentracing.util GlobalTracer)))

(def ^:dynamic ^Tracer *tracer*
  "An Tracer object representing the standard tracer for trace operations.

  Defaults to the value returned by GlobalTracer.get()."
  (GlobalTracer/get))

;; Span
;; ----

(defn active-span
  "Returns the current active span."
  []
  (when *tracer*
    (.activeSpan (.scopeManager *tracer*))))

(defmacro with-active-span
  "Convenience macro for setting sym to the current active span.  Will
  evaluate to nil if there are no active-spans."
  [sym & body]
  `(when-let [~sym (active-span)]
     ~@body))

(defn context
  "Returns the associated SpanContext of a span."
  ([]
   (with-active-span s
     (context s)))
  ([^Span span]
   (.context span)))

(defn finish
  "Sets the end timestamp to now and records the span.  Can also supply an explicit timestamp in microseconds."
  ([]
   (with-active-span s
     (finish s)))
  ([^Span span]
   (.finish span))
  ([^Span span ^long timestamp]
   (.finish span timestamp)))

(defn baggage-item
  "Returns the value of the baggage item identified by the given key, or
  nil if no such item could be found."
  ([^String key]
   (with-active-span s
     (baggage-item s key)))
  ([^Span span ^String key]
   (.getBaggageItem span key)))

(defn log
  "Logs value v on the span.

  Can also supply an explicit timestamp in microseconds."
  ([v]
   (with-active-span s
     (log s v)))
  ([^Span span v]
   (cond
     (map? v) (.log span ^java.util.Map (walk/stringify-keys v))
     :else    (.log span ^String (str v))))
  ([^Span span v ^Long timestamp]
   (cond
     (map? v) (.log span timestamp ^java.util.Map (walk/stringify-keys v))
     :else    (.log span timestamp ^String (str v)))))

(defn set-baggage-item
  "Sets a baggage item on the Span as a key/value pair."
  ([^String key ^String val]
   (with-active-span s
     (set-baggage-item s key val)))
  ([^Span span ^String key ^String val]
   (.setBaggageItem span key val)))

(defn set-baggage-items
  "Sets baggage items on the Span using key/value pairs of a map.

  Note: Will automatically convert keys into strings."
  ([map]
   (with-active-span s
     (set-baggage-items s map)))
  ([^Span span map]
   (when (map? map)
     (doseq [[k v] map]
       (set-baggage-item span
                         (if (keyword? k) (name k) (str k))
                         (str v))))
   span))

(defn set-operation-name
  "Sets the string name for the logical operation this span represents."
  ([^String name]
   (with-active-span s
     (set-operation-name s name)))
  ([^Span span ^String name]
   (.setOperationName span name)))

(defn set-tag
  "Sets a key/value tag on the Span."
  ([^String key value]
   (with-active-span s
     (set-tag s key value)))
  ([^Span span ^String key value]
   (cond
     (instance? Boolean value) (.setTag span key ^Boolean value)
     (instance? Number value)  (.setTag span key ^Number value)
     :else                     (.setTag span key ^String (str value)))))

(defn set-tags
  "Sets/adds tags on the Span using key/value pairs of a map.

  Automatically converts keys into strings.  Overrides any existing tags with the same keys."
  ([m]
   (with-active-span s
     (set-tags s m)))
  ([^Span s m]
   (when (map? m)
     (doseq [[k v] m]
       (set-tag s
                (if (keyword? k) (name k) (str k))
                v)))
   s))

;; with-span
;; ---------

(s/def :opentracing/microseconds-since-epoch int?)
(s/def :opentracing/span #(instance? Span %))
(s/def :opentracing/span-context #(instance? SpanContext %))
(s/def :opentracing.span-data/name string?)
(s/def :opentracing.span-data/tags map?)
(s/def :opentracing.span-data/ignore-active? boolean?)
(s/def :opentracing.span-data/timestamp :opentracing/microseconds-since-epoch)
(s/def :opentracing.span-data/child-of (s/nilable
                                        (s/or :span :opentracing/span
                                              :span-context :opentracing/span-context)))
(s/def :opentracing.span-data/finish? boolean?)

(s/def :opentracing/span-data
  (s/keys :req-un [:opentracing.span-data/name]
          :opt-un [:opentracing.span-data/tags
                   :opentracing.span-data/ignore-active?
                   :opentracing.span-data/timestamp
                   :opentracing.span-data/child-of
                   :opentracing.span-data/finish?]))

(s/def :opentracing.span-ref/from :opentracing/span)
(s/def :opentracing.span-ref/finish? boolean?)
(s/def :opentracing/span-ref
  (s/keys :req-un [:opentracing.span-ref/from]
          :opt-un [:opentracing.span-ref/finish?]))

(s/def :opentracing/span-init
  (s/or :existing :opentracing/span-ref
        :new :opentracing/span-data))

(s/def :opentracing/span-binding
  (s/spec
   (s/cat :span-sym simple-symbol?
          :span-spec any?)))

(defn ^:private build-new-span
  "Given a span-data, create and return a new span."
  ^Span [span-data]
  (let [builder (sb/build-span *tracer* (:name span-data))]
    (when-let [tags# (:tags span-data)]
      (sb/add-tags builder tags#))
    (when (:ignore-active? span-data)
      (sb/ignore-active builder))
    (when-let [start-ts# (:start-timestamp span-data)]
      (sb/with-start-timestamp builder start-ts#))
    (when-let [parent# (:child-of span-data)]
      (sb/child-of builder parent#))
    (sb/start builder)))

(s/fdef build-new-span
  :args (s/cat :span-data :opentracing/span-data)
  :ret :opentracing/span)

(defn ^:internal ^:no-doc get-span*
  "Given a span-init, return the existing or new span."
  [span-init]
  (let [conformed-span-init (s/conform :opentracing/span-init span-init)]
    (if (= :clojure.spec.alpha/invalid conformed-span-init)
      (throw (ex-info "with-span binding failed to conform to :opentracing/span-init"
                      (s/explain-data :opentracing/span-init span-init)))
      (case (first conformed-span-init)
        :new (build-new-span span-init)
        :existing (:from span-init)))))

(s/fdef get-span*
  :args (s/cat :span-init :opentracing/span-init)
  :ret :opentracing/span)

(defmacro with-span
  "Evaluates body in the scope of a generated span.

  binding => [span-sym span-init-spec]

  span-init-spec must evaluate at runtime to a value conforming to
  the :opentracing/span-init spec."
  [binding & body]
  (let [s (binding 0)
        m (binding 1)]
    `(let [m#  ~m
           ~s  (get-span* m#)]
       (try
         (with-open [^Scope _# (.activate (.scopeManager *tracer*)
                                          ~s)]
           ~@body)
         (finally
           (when (:finish? m# true)
             (finish ~s)))))))

(s/fdef with-span
  :args (s/cat :binding :opentracing/span-binding
               :body    (s/* any?)))
