# opentracing-clj
[![CircleCI](https://circleci.com/gh/alvinfrancis/opentracing-clj.svg?style=svg)](https://circleci.com/gh/alvinfrancis/opentracing-clj)

Opentracing API support for Clojure built on top of
[opentracing-java](https://github.com/opentracing/opentracing-java).

## Usage

### Creating Spans

The provided `with-span` macro allows for creation of spans.

``` clojure
(require '[opentracing-clj.core :as tracing])

(tracing/with-span [s {:name "span-name"
                       :tags {:component "test"
                              :span.kind :server}}]
  ...)
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
`io.opentracing.util.GlobalTracer.get()`

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
