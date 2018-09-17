# opentracing-clj [![CircleCI](https://circleci.com/gh/alvinfrancis/opentracing-clj.svg?style=svg)](https://circleci.com/gh/alvinfrancis/opentracing-clj)

Opentracing API support for Clojure built on top of
[opentracing-java](https://github.com/opentracing/opentracing-java).

## Installation

Add the following dependency to your project or build file:

```
[opentracing-clj "0.1.0"]
```

## Documentation

- [API Docs](http://alvinfrancis.github.com/opentracing-clj)

## Usage

### Creating Spans

The provided `with-span` macro allows for easy creation of spans.  By
default, spans will be set as children of unfinished spans started
prior.

``` clojure
(require '[opentracing-clj.core :as tracing])

(tracing/with-span [s {:name "span-name"
                       :tags {:component "test"
                              :span.kind :server}}]
  ...)
```

The `with-span` macro requires a `:name` set for the span init binding
along with the following options for customizing the span.

- `:tags` - A map of tags (will force keys or values into strings as necessary)
- `:ignore-active?` - Set whether the created span should be set as child of the current scoped span
- `:timestamp` - Start timestamp of the span in microseconds
- `:child-of` - Manually set which span (or span context) the new span should be child of
- `:finish?` - Set whether the span should be finished at the end of the scope

Note that spans created by opentracing-clj are also compatible with
those created by opentracing-java (and vice versa); keeping the span
nesting intact.

### Manipulating Spans

Functions are provided for manipulating spans.  Functions that work on
spans will default to the current active span unless explicity
specified.

``` clojure
(tracing/with-span [s {:name "test"}]
  (tracing/log "test") ; log against current active span
  ;; above is equivalent to (tracing/log s "test")
  (tracing/log {:some :map}) ; can also log maps
  (tracing/set-tags {:a 1 :b "val"}) ; add tags to current span
  (tracing/set-baggage-items {:baggage1 true :baggage2 "test"}) ; adds baggage to span for propagation across contexts
  )

(tracing/log "no-op") ; span functions are no-op (and evaluate to nil) if there is no active span
```

### Ring Middleware

Middleware for instrumenting Ring request/responses is provided.

``` clojure
(require '[opentracing-clj.ring :as tracing.ring])

(def app (-> handler (tracing.ring/wrap-opentracing)))
```

The middleware provides sane defaults for span naming and tagging.
The span name defaults to `http-method url` (e.g. `GET /test`).
The following semantic tags are also set: `http.method`, `http.url`, `http.status_code`.

The naming and tagging behaviour can be overriden by providing your
own functions for providing both.

``` clojure
(defn operation-name
  [ring-request]
  (str (:server-name ring-request) ":" (:server-port ring-request) (:uri ring-request)))

(defn request-tags
  [ring-request]
  {:http.protocol (:protocol ring-request)})

(defn response-tags
  [ring-response]
  {:http.date (-> ring-response :headers (get "Date"))})

(def app (-> handler (tracing.ring/wrap-opentracing operation-name request-tags response-tags)))
```

### Propagation

Support is currently available for span context propagation using text
map and HTTP header carrier formats.

``` clojure
(require '[opentracing-clj.propagation :as propagation])

(tracing/with-span [s {:name "test"}]
  (let [headers (propagation/inject :http)] ; is equivalent to (propagation/inject s :http)
    ... ; headers will be a map of the span context for use when making an HTTP call
    ))

(let [response (http-ring-response) ; assuming this is an HTTP call returning a ring spec response
      ctx      (propagation/extract (:headers request) :http)]
  (tracing/with-span [s {:name     "child-of-propagation"
                         :child-of ctx}]
    ... ; this span will be recorded as a child of the span context propagated through the HTTP call
    ))
```

### Tracer

The tracer used by opentracing-clj defaults to the value returned by
`io.opentracing.util.GlobalTracer.get()`.  Alternatively,
opentracing-clj exposes the underlying tracer via a dynamic var.

``` clojure

;; Set the root binding of the tracer to change the default value.
;; The following sets the tracer to the value return by TracerResolver.

(import '[io.opentracing.contrib.tracerresolver TracerResolver])
(alter-var-root #'tracing/tracer (TracerResolver/resolveTracer))

;; The tracer instance can also be set for a particular scope by using binding

(binding [tracing/*tracer* (other-tracer)]
  (tracing/with-span [s {:name "test"}]
    ; traces will use the tracer returned by other-tracer
    ...))

```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
