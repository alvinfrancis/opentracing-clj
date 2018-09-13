(ns opentracing-clj.core
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

  Defaults to the value returned by GlobalTracer.get().  Can be set with init!."
  (GlobalTracer/get))

;; Span
;; ----

(defn active-span
  "Returns the current active span."
  []
  (when *tracer*
    (.activeSpan *tracer*)))

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

(defn get-baggage-item
  "Returns the value of the baggage item identified by the given key, or
  nil if no such item could be found."
  ([^String key]
   (with-active-span s
     (get-baggage-item s key)))
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
     (let [sm (walk/stringify-keys map)]
       (doseq [[k v] sm]
         (set-baggage-item span k v))))
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
  "Sets tags on the Span using key/value pairs of a map.

  Note: Will automatically convert keys into strings."
  ([m]
   (with-active-span s
     (set-tags s m)))
  ([^Span s m]
   (when (map? m)
     (let [sm (walk/stringify-keys m)]
       (doseq [[k v] sm]
         (set-tag s k v))))
   s))

;; with-span
;; ---------

(s/def :opentracing/microseconds-since-epoch int?)
(s/def :opentracing/span #(instance? Span %))
(s/def :opentracing/span-context #(instance? SpanContext %))
(s/def :opentracing.span-init/name string?)
(s/def :opentracing.span-init/tags map?)
(s/def :opentracing.span-init/ignore-active? boolean?)
(s/def :opentracing.span-init/timestamp :opentracing/microseconds-since-epoch)
(s/def :opentracing.span-init/child-of (s/nilable
                                        (s/or :opentracing/span
                                              :opentracing/span-context)))
(s/def :opentracing.span-init/finish? boolean?)

(s/def :opentracing/span-init
  (s/keys :req-un [:opentracing.span-init/name]
          :opt-un [:opentracing.span-init/tags
                   :opentracing.span-init/ignore-active?
                   :opentracing.span-init/timestamp
                   :opentracing.span-init/child-of
                   :opentracing.span-init/finish?]))

(s/def :opentracing/span-binding
  (s/spec
   (s/cat :span-sym simple-symbol?
          :span-spec any?)))

(defmacro with-span
  "Evaluates body in the scope of a generated span.

  binding => [span-sym span-init-spec]

  span-init-spec must evaluate at runtime to a value conforming to
  the :opentracing/span-init spec."
  [bindings & body]
  (let [s (bindings 0)
        m (bindings 1)]
    `(let [m# ~m]
       (if (s/valid? :opentracing/span-init m#)
         (let [sb# (sb/build-span *tracer* (:name m#))]
           (when-let [tags# (:tags m#)]
             (sb/add-tags sb# tags#))
           (when (:ignore-active? m#)
             (sb/ignore-active sb#))
           (when-let [start-ts# (:start-timestamp m#)]
             (sb/with-start-timestamp sb# start-ts#))
           (when-let [parent# (:child-of m#)]
             (sb/child-of sb# parent#))
           (with-open [^Scope scope# (sb/start sb# (or (nil? (:finish? m#))
                                                       (:finish? m#)))]
             (let [~s (.span scope#)]
               ~@body)))
         (throw (ex-info "with-span binding failed to conform to :opentracing/span-init"
                         (s/explain-data :opentracing/span-init m#)))))))

(s/fdef with-span
  :args (s/cat :binding :opentracing/span-binding
               :body    (s/* any?)))

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
