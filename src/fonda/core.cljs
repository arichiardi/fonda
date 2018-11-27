(ns fonda.core
  (:require [fonda.anomaly]
            [fonda.execute :as e]
            [fonda.runtime :as r]
            [fonda.step :as st]
            [cljs.spec.alpha :as s]))

(s/def ::config
  (s/keys :opt-un [::r/anomaly?
                   ::r/log-exception
                   ::r/log-anomaly
                   ::r/log-step-fn
                   ::r/initial-ctx]))

(s/def ::steps (s/coll-of ::st/step))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;
(s/fdef execute
  :args (s/cat :config ::config
               :steps ::steps
               :context ::r/ctx
               :on-success ::r/on-success
               :on-anomaly ::r/on-anomaly
               :on-exception ::r/on-exception))
(defn execute
  "Sequentially executes the series of given `steps`.

  Each step function - tap or processor - can be synchronous or asynchronous.

  - `config`: A map with:
      - [opt] anomaly?      A function that gets a map and determines if it is an anomaly.
      - [opt] log-exception A function gets called with the FondaContext record when there is an exception.
      - [opt] log-anomaly   A function that gets called with the FondaContext record when a step returns an anomaly.
      - [opt] log-success   A function that gets called with the FondaContext record after all steps succeeded.
      - [opt] initial-ctx   The context data initializes the context. Must be a map.

  - `steps`: Each item on the `steps` collection must be either a Tap, or a Processor.

      Tap:
       - tap:  A function that gets the context but doesn't augment it.
       - name: The name of the step

      Processor:
       - processor: A function that gets the context and assocs the result into it on the given path
       - path:     Path where to assoc the result of the processor
       - name:     The name of the step

  - `ctx`          The runtime context, merged to the initial context. Must be a map.
  - `on-success`   Callback that gets called with the context if all the steps succeeded.
  - `on-anomaly`   Callback that gets called with an anomaly when any step returns one.
  - `on-exception` Callback that gets called with an exception when any step triggers one.
  "
  ([config steps ctx on-sucess on-anomaly on-exception]

   (let [{:keys [anomaly?
                 log-exception
                 log-anomaly
                 log-success
                 log-step-fn]} config]

     (-> (r/map->FondaContext
          {:anomaly?      (or anomaly? fonda.anomaly/anomaly?)
           :log-exception log-exception
           :log-anomaly   log-anomaly
           :log-success   log-success
           :ctx           (merge (:initial-ctx config) ctx)
           :on-success    on-sucess
           :on-anomaly    on-anomaly
           :on-exception  on-exception
           :queue         (st/steps->queue steps)
           :stack         []
           :step-log      []
           :log-step-fn   (or log-step-fn e/default-log-step-fn)})
         (e/execute-steps)
         (e/execute-loggers)
         (e/deliver-result)))))
