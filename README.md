# opentracing-clj [![Build Status](https://travis-ci.org/alvinfrancis/opentracing-clj.svg?branch=master)](https://travis-ci.org/alvinfrancis/opentracing-clj) [![Clojars Project](https://img.shields.io/clojars/v/opentracing-clj.svg)](https://clojars.org/opentracing-clj)
Opentracing API support for Clojure built on top of
[opentracing-java](https://github.com/opentracing/opentracing-java).

## Installation

Add the following dependency to your project or build file:

```
[opentracing-clj "0.2.1"]
```

## Requirements

Supports Opentracing 0.33.0 and onward.

## Documentation

- [API Docs](http://alvinfrancis.github.com/opentracing-clj)

## Required Reading

In order to better understand the project, it will be helpful to be
first familiar with the [OpenTracing project](http://opentracing.io)
and [terminology](http://opentracing.io/documentation/pages/spec.html)
more specifically.

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

The `with-span` macro configures the creation of the span by
specifying necessary information through the span binding map.  The
following fields are used to configured the behaviour of `with-span`.

*Required*
- `:name` - Operation name of the span (required)

*Optional*
- `:tags` - A map of tags (will force keys or values into strings as necessary)
- `:ignore-active?` - Set whether the created span should be set as child of the current scoped span
- `:timestamp` - Start timestamp of the span in microseconds
- `:child-of` - Manually set which span (or span context) the new span should be child of
- `:finish?` - Set whether the span should be finished at the end of the scope

Alternatively, instead of creating a new span, the `with-span` macro
can also accept a span in the case of activating an existing span
within the scope of the `with-span`.  In this instance, the following
fields are used to configure the behaviour of `with-span`.

*Required*
- `:from` - Span to activate within the scope of the body

*Optional*
- `:finish?` - Set whether the span should be finished at the end of the scope

``` clojure
(tracing/with-span [s {:from some-span
                       :finish? false}]
  ...)
```

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

### Async

`with-span` will only set the active span for the thread and scope
where it was invoked.  Code that is run on a separate thread will not
pick up the active span.

``` clojure
(tracing/with-span [s {:name "test"}]
  (let [async (future (tracing/active-span))]
    (= @async s) ; => false
    ))
```

To keep a span active in a separate thread, the active span can be
passed directly to `with-span`.

``` clojure
(tracing/with-span [s0 {:name "test"}]
  (let [async (future
                (tracing/with-span [s1 {:from    s0
                                        :finish? false}] ; NOTE: finish? is set to false to prevent early finishing of the span
                  (= s1 s0 (tracing/active-span)) ; => true
                  (tracing/active-span)))]
    (= s0 @async) ; => true
    ))
```

It is important to remember that `with-span` defaults to finishing a
span at the end of its scope.  This can cause exceptions if multiple
paths can cause a span to be finished.

``` clojure
;; This would complain about finishing an already finished span since
;; the async with-span would finish the span before the main thread
;; with-span.

(tracing/with-span [s0 {:name "test"}]
  (let [async-1 (future (tracing/with-span [s1 {:from s0}] ...))]
    @async-1
    ...))
```

### Exceptions

Since `with-span` will finish a span unless configured otherwise, any
additional data one wishes to add to the span relating to the exception
should be done within the macro.  The span will still be finished at
the end of the scope of `with-span`.

``` clojure
(tracing/with-span [s {:name "test"}]
  (try
    (throw (ex-info "test" nil))
    (catch Exception e
      (tracing/log "exception")
      (tracing/set-tags {:event "error"}))))
```

### Propagation

Support is currently available for span context propagation using text
map and HTTP header carrier formats.

``` clojure
(require '[opentracing-clj.propagation :as propagation])

(tracing/with-span [s {:name "test"}]
  (let [headers (propagation/inject :http)] ; is equivalent to (propagation/inject (tracing/context s) :http)
    ... ; headers will be a map of the span context for use when making an HTTP call
    ))

(defn ring-handler
  [request]
  (let [ctx (propagation/extract (:headers request) :http)] ; extract span context from request headers
    (tracing/with-span [s {:name     "child-of-propagation"
                           :child-of ctx}]
      ... ; this span will be recorded as a child of the span context propagated through the HTTP call to this handler
      )))
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

### Tracer

The tracer used by opentracing-clj defaults to the value returned by
`io.opentracing.util.GlobalTracer.get()`.  Alternatively,
opentracing-clj exposes the underlying tracer via a dynamic var.

``` clojure

;; Set the root binding of the tracer to change the default value.
;; The following sets the tracer to the value return by TracerResolver.

(import '[io.opentracing.contrib.tracerresolver TracerResolver])
(alter-var-root #'tracing/*tracer* (TracerResolver/resolveTracer))

;; The tracer instance can also be set for a particular scope by using binding

(binding [tracing/*tracer* (other-tracer)]
  (tracing/with-span [s {:name "test"}]
    ; traces will use the tracer returned by other-tracer
    ...))

```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
