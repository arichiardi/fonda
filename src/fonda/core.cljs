(ns fonda.core
  (:require [fonda.execute :as e]
            [fonda.step :as st]))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;

(defn execute
  "Sequentially executes the series of given `steps`.

  Each step function - tap or processor - can be synchronous or asynchronous.

  - config: A map with:
      - [opt] anomaly?             A function that gets a map and determines if it is an anomaly.
      - [opt] ctx                  The data that initializes the context. Must be a map.
      - [opt] mock-fns             A map of functions that will replace the function on the step, matching the map
                                   key with the step name
      - [opt] anomaly-handlers     A map of functions indexed by step name that get called with a map
                                   `{:ctx <ctx> :anomaly <anomaly>}` when the step returns an anomaly.
      - [opt] exception-handlers   A map of functions indexed by step name that get called with a map
                                   `{:ctx <ctx> :exception <exception>}` when the step triggers an exception.
      - [opt] callbacks-wrapper-fn A function that gets called with the value of the on-* and the result of the step
                                   `(fn [on-callback-val ctx step-res] ...)`

  - steps: Each item on the `steps` collection must be either a Tap, a Processor, or an Injector

      Tap:
       - tap:  A function that gets the context but doesn't augment it.
       - name: The name of the step

      Processor:
       - [processor] or [fn]: A function that gets the context and assocs the result into it on the given path
       - path:                Path where to assoc the result of the processor
       - name:                The name of the step

      Injector:
       - inject: A function that gets the context and returns either a step or a collection of them.
                 The step(s) returned will be executed right after the injector step and just before the next steps. Can be asynchronous.
       - name:   The name of the injector step

  - on-success   Callback that gets called with the context if all the steps succeeded.
  - on-exception Callback that gets called with an exception when any step triggers one.
  - [opt] on-anomaly   Callback that gets called with an anomaly when any step returns one."
  ([config steps on-exception on-success]
   (execute config steps on-exception on-success nil))
  ([config steps on-exception on-success on-anomaly]
   (-> config
       (merge {:steps        steps
               :on-anomaly   on-anomaly
               :on-exception on-exception
               :on-success   on-success})
       (e/fonda-context)
       (e/execute-steps)
       (e/execute-handlers)
       (e/deliver-result))))
