# Fonda

An async pipeline approach to functional core - imperative shell from by Gary Bernhardt's [Boundaries talk.](https://www.destroyallsoftware.com/talks/boundaries).

---
## WIP

### Step callbacks
Each step admits now the following keywords: `on-start`, `on-complete`, `on-success` and `on-error`.
The values should be functions with the signature `(fn [ctx step-res]`. If the value is not a function, and a `callbacks-wrapper-fn` function is given
on the configuration, and that function will be called with that value.

### Global callbacks
Global callbacks can now be a value that will be passed to the `callbacks-wrapper-fn`.

### callbacks-wrapper-fn
As described above, if a `callbacks-wrapper-fn` function is provided on the configuration, the steps and global callbacks can be values instead of functions,
and those values will be passed to the callback wrapper function. It should have the signature `(fn [callback-value ctx step-res])`.
Can be used to dispatch events instead of calling functions. And because the values of the step callbacks are now data, it can be tested.

### Steps and callback functions signature changed to receive the result from the previous step
The first steps's function signature remains the same, but consequent steps receive one more argument `(fn [ctx prev-step-res])`.
The first argument remains the same - the context - and the second argument is the result of the previous step.

### Path is now optional
If a step doesn't define a `:path`,  the step will not augment the context, and the result of the step can only be used by the next step

### Mocked functions
Now is possible to mock the functions on the steps by passing a map of mocked functions to the fonda configuration.

### `:fn` or `:processor`
We realized the `:processor` keyword is too long and verbose so now it's also possible to define processor steps with the `:fn` keyword

---


## Minimal Viable Example

This example illustrates `fonda`'s basic mechanics:

```clojure
(ns example.simple
  (:require [cljs-http.client :as http]
            [cljs.core.async :as cca :include-macros true]
            [clojure.set :as set]
            [fonda.core :as fonda]))

(defn fetch-user
  [ctx]
  (http/get "http://insecure-endpoint.com"
            {:basic-auth (select-keys ctx [:username :password])}))

(fonda/execute
 {:ctx {:username js/process.USER
        :password js/process.PASSWORD}}

 [{:processor  :example.simple/fetch-user                ;; can be either a function or a keyword
   :path       [:github-response]}

  {:processor  :example.simple/github-response->things   ;; pure function [prev-step-res ctx] -> step-res
   :path       [:github-things]}]

 ;; on-exception
 (fn [exception]
   (handle-exception exception))

 ;; on-success
 (fn [res ctx]
   (handle-success (:github-things res))))
```

*HINT*: The parameter order makes it easy to partially apply `execute` for leaner call sites.

---

Fonda sequentially executes a series of [steps](#trivia), one after the other, possibly augmenting a context map, and passing the result to the next one. The steps can be synchronous or asyncronous. After the steps are run, the termination callbacks will be executed.

If a `js/Error`, an exception in `fonda` parlance, is thrown it will be automatically caught and the chain short circuits and the `on-exception` function is called with the `js/Error`.

Exceptions are things we can't and don't want to recover from, like unexpected bugs.

## Anomalies

Anomaly is

  > Deviation or departure from the normal or common order, form, or rule.

There is a stark contrast between anomalies and JavaScript `js/Error`s, Promise rejections or Java `Exception`s: anomalies are data that define a recoverable error, like receiving a 409 (conflict) http status code that you can retry.

Anomalies are first class citizens in `fonda` and by default they are maps containing the [`:cognitect.anomalies/anomaly`](https://github.com/cognitect-labs/anomalies) key.

If `anomaly?` is truthy and an anomaly is returned by a step the whole pipeline short circuits and the `on-anomaly` function is called.

It is also possible to redefine what an anomaly is by passing a config predicate, `anomaly?`, so that client code can have its own representation of an anomaly as data.

The following section describes the parameters `fonda/execute` accepts.

## Parameters

- **config** - static configuration map

    | Key                   | Optional? | Notes                                                                                                                                           |
    |-----------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
    | `:anomaly?`           | Yes       | A boolean or a function that gets a map and determines if it is an anomaly.                                                                     |
    | `:ctx`                | Yes       | The data that initializes the context. Must be a map, `{}` by default.                                                                          |
    | `:mocked-fns`         | Yes       | A map of functions that will replace the functions on the step, indexed by step name.                                                           |
    | `:anomaly-handlers`   | Yes       | A map from step name keyword to function that gets called with a map `{:ctx <ctx> :anomaly <anomaly>}` when the step returns an anomaly.        |
    | `:exception-handlers` | Yes       | A map from step name keyword to function that gets called with a map `{:ctx <ctx> :exception <exception>}` when the step triggers an exception. |

- **steps** - each item must be either a `Tap` or a `Processor`, or a `Injector`

  - tap

    | Key            | Optional? | Notes                                                                                                                                                                                                                          |
    |----------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
    | `:tap`         | No        | A function that gets the context but doesn't augment it. If it succeeds the result is ignored. If asynchronous it will still block the pipeline and interrupt the execution whenever either an anomaly or an exception happen. |
    | `:name`        | Yes       | The name of the step as string or keyword                                                                                                                                                                                      |
    | `:on-start`    | Yes       | A function with the signature `(fn [ctx])`, of a value if `callbacks-wrapper-fn` is provided on the configuration. It is called before the step is executed                                                                    |
    | `:on-complete` | Yes       | A function with the signature `(fn [ctx result-or-exception-or-anomaly])`, of a value if `callbacks-wrapper-fn` is provided on the configuration. Called after the step is executed                                            |
    | `:on-success`  | Yes       | A function with the signature `(fn [ctx result])`, of a value if `callbacks-wrapper-fn` is provided on the configuration. Called after the step sucessfully executed                                                           |
    | `:on-error`    | Yes       | A function with the signature `(fn [ctx step-exception-or-anomaly])`, of a value if `callbacks-wrapper-fn` is provided on the configuration. Called when the step returns an exception or an anomaly.                          |

  - processor

    | Key                   | Optional? | Notes                                                                                                                                                                                                                                                                                             |
    |-----------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
    | `:processor` or `:fn` | No        | A function that gets the context and returns data. The data is [assoced-in](https://clojuredocs.org/clojure.core/assoc-in) at the given path Can be asynchronous. If asynchronous it will still block the pipeline and interrupt the execution whenever either an anomaly or an exception happen. |
    | `:path`               | Yes       | Path where to assoc the result of the processor. If not given, the step will not augment the context.                                                                                                                                                                                             |
    | `:name`               | Yes       | The name of the step as string or keyword.                                                                                                                                                                                                                                                        |
    | `:on-start`           | Yes       | Same as in tap                                                                                                                                                                                                                                                                                    |
    | `:on-complete`        | Yes       | Same as in tap                                                                                                                                                                                                                                                                                    |
    | `:on-success`         | Yes       | Same as in tap                                                                                                                                                                                                                                                                                    |
    | `:on-error`           | Yes       | Same as in tap                                                                                                                                                                                                                                                                                    |

  - injector

    | Key       | Optional? | Notes                                                                                                                                                                                                         |
    |-----------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
    | `:inject` | No        | A function that gets the context and returns either a step or a collection of steps. The step(s) returned will be executed right after the injector step and just before the next steps. Can be asynchronous. |
    | `:name`   | Yes       | The name of the injector step as string or keyword                                                                                                                                                            |


- **on-exception**          Function with the signature `(fn [ctx exception])` called with the context and an exception when any of the steps throws one.
- **on-success**            Function with the signature `(fn [ctx last-step-result])` called with the context if all steps succeed, and the last step result.
- [Optional] **on-anomaly** Function with the signature `(fn [ctx exception])` called in case of anomaly with the context and the anomaly data itself.


## Full Example

```clojure
(ns example.full
  ...)

(defn get-remote-thing
  [ctx]
  (ajax/GET "http://remote-thing-url.com" {:params (:remote-thing-params ctx)}))

(defn print-remote-thing
  [remote-thing-response ctx]
  (println "the remote thing response was:" remote-thing-response))

(defn mocked-remote-thing (constantly :mocked-result))

(fonda/execute
  {:ctx {:env-var-xyz "value",
         :remote-thing-params {:p1 "p1" :p2 "p2"}
         :other-remote-thing-responses []}

   ;; Being replaced by the mock
   :mocked-fns       {:get-remote-thing mocked-remote-thing}

   :anomaly-handlers {:get-remote-thing (fn [{:keys [anomaly]}]
                                           (post-error-to-log-server anomaly))}
   :exception-handlers {:get-remote-thing (fn [{:keys [exception]}]
                                              (js/console.log "An exception retrieving the remote thing occurred:" exception))}}

  ;; Doesn't run this function, instead it runs the provided mock
  [{:processor  :example.full/get-remote-thing
    :name       "get-remote-thing"
    :path       [:remote-thing-response]
    :on-start (fn [ctx] (println "Going to fetch the remote thing")
    :on-success (fn [ctx res] (println "got the remote thing with response:" res))
    :on-error (fn [ctx err-or-anomaly] (println "error fetching the remote thing:" err-or-anomaly))
    :on-complete (fn [ctx result-or-exception-or-anomaly] (println "Done fetching the remote thing, the result (or error) is:" result-or-exception-or-anomaly)))}

   {:tap        :example.full/print-remote-thing}

   {:processor  :example.other/parse-remote-thing-response
    :path       [:remote-thing]}

   ;; Injector returns a collection of steps to be added right after the injector step
   {:inject     (fn [{:keys [remote-thing remote-thing-response]}]
                  (->> (:more-urls remote-thing)
                       (map (fn [url]
                              {:tap (fn [{:keys [remote-thing-params]}]
                                      (ajax/POST url remote-thing-params))}))))}]

  ;; on-exception
  (fn [exception]
   (handle-exception exception))

  ;; on-success
  (fn [{:keys [remote-thing-processed]}]
   (handle-success remote-thing-processed))

  ;; on-anomaly
  (fn [anomaly]
   (handle-anomaly anomaly)))
```

## Thanks

The conception of the library has taken place during early research and
development work at [Elastic Path Software
Inc.](https://www.elasticpath.com). A heart-felt thank you goes especially to
[Matt Bishop](https://github.com/mattbishop) for supporting open source.

This package is has been basically co-maintained by [David Cerezo
Iñigo](https://github.com/ElChache), which has brought this library to the next
level, experimenting with it.

## Trivia

The name got inspired by Jane Fonda's step very successful fitness programs.

![](https://img.buzzfeed.com/buzzfeed-static/static/enhanced/webdr03/2013/8/15/10/anigif_enhanced-buzz-31474-1376578012-1.gif?downsize=700:*&output-format=auto&output-quality=auto)

As with the fitness program, `fonda` consist of well-curated 🚶‍♀️ steps 🚶‍♂️.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

Copyright 2018-2019, The Fonda [Authors](./AUTHORS)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
