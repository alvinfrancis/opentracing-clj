# opentracing-clj
[![CircleCI](https://circleci.com/gh/alvinfrancis/opentracing-clj.svg?style=svg)](https://circleci.com/gh/alvinfrancis/opentracing-clj)

Opentracing API support for Clojure built on top of [opentracing-java](https://github.com/opentracing/opentracing-java).

## Usage

```
(require '[opentracing-clj.core :as tracing])

(tracing/with-span [s {:name "span-name"
                       :tags {:component "test"
                              :span.kind :server}}]
  ...)
```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
