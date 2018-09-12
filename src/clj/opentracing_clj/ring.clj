(ns opentracing-clj.ring
  (:require
   [clojure.string :as string]
   [opentracing-clj.core :as tracing]
   [opentracing-clj.propagation :as propagation]
   [ring.util.request]))

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
     (let [ctx (propagation/extract (:headers request) :http)]
       (tracing/with-span [s {:name     (op-name-fn request)
                              :child-of ctx
                              :tags     (request-tags-fn request)}]
         (let [response (handler (assoc request ::span s))]
           (tracing/set-tags s (response-tags-fn response))
           response))))))
