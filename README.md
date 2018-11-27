<img src="https://www.elasticpath.com/sites/all/themes/bootstrap/images/elastlic-path-logo-RGB.svg" alt="elasticpath logo" title="elasticpath" align="right" width="150"/>

# Fonda

An async pipeline approach to functional core - imperative shell from by Gary Bernhardt's [Boundaries talk.](https://www.destroyallsoftware.com/talks/boundaries)

## Asynchronous pipeline of steps

Fonda sequentially executes a series of [steps](#trivia), one after the other, augmenting a context map. The steps can be synchronous or asyncronous. After the steps are run, the termination callbacks will be executed.

Fonda distinguishes between exceptions and anomalies.

- Anomaly

  > Deviation or departure from the normal or common order, form, or rule.

Anomalies are just data that define a recoverable "business" error. There is a stark contrast between anomalies and JavaScript `js/Error`s, Promise rejections, Java `Exception`s. The latter are never meant to be caught and are usually caused by programming bugs.

Anomalies are first class citizens and by default they are maps containing the [`:cognitect.anomalies/anomaly`](https://github.com/cognitect-labs/anomalies) key.

It is possible to redefine what an anomaly is by passing a predicate, `anomaly?` to fonda.

## Usage

```clojure
(ns my-namespace.core
  (:require [fonda.core :as fonda]))

(fonda/execute config steps ctx on-success on-anomaly on-exception)
```

The parameter order makes it easy to partially apply `execute` for leaner call sites.

## Parameters

- **config** A map with:

| Key | Optional? | Notes |
|---|---|---|
| `:anomaly?` | Yes | A function that gets a map and determines if it is an anomaly |
| `:log-exception` | Yes | A function that gets called with the [log map](#log-map) when there is an exception |
| `:log-anomaly` | Yes | A function that gets called with the [log map](#log-map) when a step returns an anomaly |
| `:log-success` | Yes | A function that gets called after all the steps succeed |
| `:initial-ctx` | Yes | The data that initializes the context. Must be a map |

- **steps**: Each item on the steps collection must be either a Tap or a Processor

  - tap

    | Key | Optional? | Notes |
    |---|---|---|
    | `:tap` | No | A function that gets the context but doesn't augment it |
    | `:name` | No | The name of the step |

  - processor

    | Key | Optional? | Notes |
    |---|---|---|
    | `:processor` | No | A function that gets the context returns a result that is [assoc](https://clojuredocs.org/clojure.core/assoc)ed into the context on the given path|
    | `:path` | No | Path where to assoc the result of the processor |
    | `:name` | No | The name of the step |


- **ctx**          The runtime context, merged to the initial context. Must be a map.
- **on-success**   Callback that gets called with the context if all steps succeeded.
- **on-anomaly**   Callback that gets called with an anomaly when any step returns one.
- **on-exception** Callback that gets called with an exception when any step triggers one.

### Error and Anomaly Handling

- If any step returns an anomaly, or triggers an exception, the execution of the steps stops. Then, one of the loggers will be called
 (non blocking, he result of the logger is ignored), followed by one of the callbacks.

- If any step returns an anomaly, the log-anomaly will be called with the [log map](#log-map) and then the on-anomaly callback

- If any step triggers an exception, the log-exception will be called with the [log map](#log-map) and then on-exception callback.

- Otherwise, if all the steps where executed successfully, the log-success will be called with the [log map](#log-map), and then the on-success callback.

#### Processor steps

Processors are maps with the following keys:

- **:processor** A function that gets a context map and returns data. Can be asynchronous.
                 If it returns an anomaly, or it triggers an error, further steps execution will be short-circuited.
- **:path** A vector that determines where in the context will the resolve result be associated.
- **:name** A descriptive name for the step

#### Tap steps

Taps are maps with the following keys:

- **:tap**  A function that gets the context map. If it succeeds, the result is then ignored.
            It will still block the steps processing if it is asynchronous, and it will interrupt the steps execution if it returns an anomaly, or it triggers an exception
- **:name** A descriptive name for the step

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
   (call-on-success-cb remote-thing-processed))

  ;; on-anomaly
  (fn [anomaly]
   (call-on-anomaly-cb anomaly))

  ;; on-error
  (fn [error]
   (call-on-error-cb error)))

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
