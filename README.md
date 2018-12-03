<img src="https://www.elasticpath.com/sites/all/themes/bootstrap/images/elastlic-path-logo-RGB.svg" alt="elasticpath logo" title="elasticpath" align="right" width="150"/>

# Fonda

An async pipeline approach to functional core - imperative shell from by Gary Bernhardt's [Boundaries talk.](https://www.destroyallsoftware.com/talks/boundaries)

## Minimal Viable Example

This example illustrates `fonda`'s basic mechanics:

```clojure
(ns fonda.example.simple
  (:require [cljs-http.client :as http]
            [cljs.core.async :as cca :include-macros true]
            [clojure.set :as set]
            [fonda.core :as fonda]))

(fonda/execute
 {:initial-ctx     {:username js/process.USER
                    :password js/process.PASSWORD}}

 [{:processor      (fn [ctx]
                     (http/get "http://insecure-endpoint.com"
                               {:basic-auth (select-keys ctx [:username :password])}))
   :name           "get-all-things"
   :path           [:github-response]}

  {:processor      github-response->things   ;; Pure function - ctx in -> ctx out
   :name           "github-response->things"
   :path           [:github-things]}]

 {:foo :bar}

 ;; on-success
 (fn [ctx]
   (handle-success (:github-things ctx)))

 ;; on-exception
 (fn [exception]
   (handle-exception exception)))
```
*HINT*: The parameter order makes it easy to partially apply `execute` for leaner call sites.

---

Fonda sequentially executes a series of [steps](#trivia), one after the other, augmenting a context map. The steps can be synchronous or asyncronous. After the steps are run, the termination callbacks will be executed.

If a `js/Error`, an exception in `fonda` parlance, is thrown it will be automatically caught and the chain short circuits. Then the following things happen in order:

 * The `log-exception` function is called, if present, with the [log map](#log-map).
 * The `on-exception` function is called with the `js/Error`.

Exceptions are things we can't and don't want to recover from, like unexpected bugs.

## Anomalies

Anomaly is

  > Deviation or departure from the normal or common order, form, or rule.

There is a stark contrast between anomalies and JavaScript `js/Error`s, Promise rejections or Java `Exception`s: anomalies are data that define a recoverable error, like receiving a 409 (conflict) http status code that you can retry.

Anomalies are first class citizens in `fonda` and by default they are maps containing the [`:cognitect.anomalies/anomaly`](https://github.com/cognitect-labs/anomalies) key.

It is also possible to redefine what an anomaly is by passing a config predicate, `anomaly?`, so that client code can have its own representation of an anomaly.

An anomaly returned by a step will also short circuit, with the following happening afterwards:

* The `log-anomaly` function is called if present.
 * The `on-anomaly` function is called.

The following section describes the parameters `fonda/execute` accepts.
 
## Parameters

- **config** - static configuration map

    | Key | Optional? | Notes |
    |---|---|---|
    | `:anomaly?` | Yes | A function that gets a map and determines if it is an anomaly |
    | `:log-exception` | Yes | A function that gets called with the [log map](#log-map) when there is an exception |
    | `:log-anomaly` | Yes | A function that gets called with the [log map](#log-map) when a step returns an anomaly |
    | `:log-success` | Yes | A function that gets called after all the steps succeed |
    | `:initial-ctx` | Yes | The data that initializes the context. Must be a map, `{}` by default. |

 A function that gets the context map. If it succeeds, the result is then ignored.
            It will still block the steps processing if it is asynchronous, and it will interrupt the steps execution if it returns an anomaly, or it triggers an exception

- **steps** - each item must be either a `Tap` or a `Processor`

  - tap

    | Key | Optional? | Notes |
    |---|---|---|
    | `:tap` | No | A function that gets the context but doesn't augment it. If it succeeds the result is ignored. If asynchronous it will still block the pipeline and interrupt the execution whenever either an anomaly or an exception happen. |
    | `:name` | No | The name of the step |

  - processor

    | Key | Optional? | Notes |
    |---|---|---|
    | `:processor` | No | A function that gets the context and returns data. The data is [assoced-in](https://clojuredocs.org/clojure.core/assoc-in) at the given path Can be asynchronous. If asynchronous it will still block the pipeline and interrupt the execution whenever either an anomaly or an exception happen. |
    | `:path` | No | Path where to assoc the result of the processor |
    | `:name` | No | The name of the step |


- **ctx**          The runtime starting context, merged to the initial context. Must be a map, `{}` by default.
- **on-success**   Callback that gets called with the context if all steps succeed.
- **on-anomaly**   Callback that gets called with an anomaly when any of the steps returns one.
- **on-exception** Callback that gets called with an exception when any of the steps throws one.

### <a name="logging"></a>Logging

Log functions are called after the steps have been executed. Log functions are non-blocking, and their returning value
is ignored. Their only parameter is the [log map](#log-map)

If all the steps succeeded, the `log-success` function will be called.

If any step return an anomaly, the `log-anomaly` will be called instead.

If any step threw an exception, the `log-exception` function will be called.


#### <a name="log-map"></a>Log map

It is a map that is passed to the logging functions.
This is different from the context passed to the steps and it is only exposed to the these functions for logging purposes.

The **log map** is a record that contains:

- **:ctx**       The context that was threaded through the steps.
- **:anomaly**   The anomaly caused by one of the steps, if any.
- **:exception** The exception caused by one of the steps _or taps_, if any.
- **:stack**     A stack with all the steps already executed. Last step in the stack is the last step executed.

## Full Example

```clojure
(fonda/execute
  {:initial-ctx     {:env-var-xyz "value"}

   :log-exception   (fn [{:keys [ctx exception]}]
                      (println "Oh noes! An exception happened:" exception))

   :log-anomaly     (fn [{:keys [ctx anomaly]}]
                      (println "Well ok, some anomaly happened:" anomaly))

   :log-success     (fn [{:keys [ctx]}]
                      (println "Operation successful!"))}

  [{:processor      (fn [ctx]
                      (ajax/GET "http://remote-thing-url.com" {:params (:remote-thing-params ctx)})
    :name           "get-remote-thing"
    :path           [:remote-thing-response]}

   {:tap            (fn [{:keys [remote-thing-response]}]
                      (println "the remote thing response was:" remote-thing-response))}

   {:processor      process-remote-thing-response ;; Pure function - ctx in - ctx out
    :name           "process-thing-response"
    :path           [:remote-thing]}]

  {:foo :bar}

  ;; on-success
  (fn [{:keys [remote-thing-processed]}]
   (handle-success remote-thing-processed))

  ;; on-anomaly
  (fn [anomaly]
   (handle-anomaly anomaly))

  ;; on-exception
  (fn [exception]
   (handle-exception exception)))

```

## Trivia

The name fonda got inspired by Jane Fonda's step fitness programs.
![](https://img.buzzfeed.com/buzzfeed-static/static/enhanced/webdr03/2013/8/15/10/anigif_enhanced-buzz-31474-1376578012-1.gif?downsize=700:*&output-format=auto&output-quality=auto)

As with the fitness program, fonda consist of well-curated steps.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

Copyright 2018 Elastic Path

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
