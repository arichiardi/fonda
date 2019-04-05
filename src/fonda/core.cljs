(ns fonda.core
  (:require [fonda.anomaly]
            [fonda.execute :as e]
            [fonda.step :as st]))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;

(defn execute
  "Sequentially executes the series of given `steps`.

  Each step function - tap or processor - can be synchronous or asynchronous.

  - config: A map with:
      - [opt] anomaly?      A function that gets a map and determines if it is an anomaly.
      - [opt] initial-ctx   The data that initializes the context. Must be a map.

  - steps: Each item on the `steps` collection must be either a Tap, or a Processor.

      Tap:
       - tap:  A function that gets the context but doesn't augment it.
       - name: The name of the step

      Processor:
       - processor: A function that gets the context and assocs the result into it on the given path
       - path:     Path where to assoc the result of the processor
       - name:     The name of the step

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
       (e/deliver-result))))
