(ns opentracing-clj.instrumentation
  (:require [opentracing-clj.core :as tracing]))


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
      (tracing/with-span [_ {:name name
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
