(ns fonda.core
  (:require [fonda.anomaly]
            [fonda.execute :as e]
            [fonda.runtime :as r]
            [fonda.step :as st]
            [cljs.spec.alpha :as s]))

(s/def ::config
  (s/keys :opt-un [::r/anomaly?
                   ::r/exception-tap
                   ::r/anomaly-tap
                   ::r/log-step-fn]))

(s/def ::steps (s/coll-of ::st/step))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;
(s/fdef execute
  :args (s/cat :config ::config
               :steps ::steps
               :ctx-seed ::r/ctx
               :on-complete ::r/on-complete
               :on-anomaly ::r/on-anomaly
               :on-exception ::r/on-exception))
(defn execute
  "Sequentially executes the series of given `steps`
  Each step tap of resolver function can be synchronous or asynchronous.
  .

  - `config`: A map with:
      - [opt] anomaly?      A function that gets a map and determines if it is an anomaly
      - [opt] exception-tap A function gets called with the runtime-context when there is an exception
      - [opt] anomaly-tap   A function that gets called with te runtime-context when a step returns an anomaly
      - [opt] log-step-fn   A function that defines how each step adds information to the log


  - `steps`: Each item on the `steps` collection must be either a TapStep, or a ResolverStep

      TapStep:
       - tap:  A function that gets the context but doesn't augment it
       - name: The name of the step

      ResolverStep:
       - resolver: A function that gets the context and assocs the result into it on the given path
       - path:     Path where to assoc the result of the resolver
       - name:     The name of the step

  - `ctx-seed` The context data that gets passed to the steps functions.
             Must be either a map, or nil

  - `on-complete'  Callback that gets called with the context if all the steps succeeded
  - `on-anomaly`   Callback that gets called with an anomaly when any step returns one
  - `on-exception` Callback that gets called with an exception when any step triggers one
  "
  ([config steps ctx-seed on-complete on-anomaly on-exception]
   (let [{:keys [anomaly?
                 exception-tap
                 anomaly-tap
                 log-step-fn]} config]

     (-> (r/map->RuntimeContext
           {:anomaly?      (or anomaly? fonda.anomaly/anomaly?)
            :exception-tap exception-tap
            :anomaly-tap   anomaly-tap
            :ctx           (or ctx-seed nil)
            :on-complete   on-complete
            :on-anomaly    on-anomaly
            :on-exception  on-exception
            :queue         (st/steps->queue steps)
            :step-log      []
            :log-step-fn   (or log-step-fn e/default-log-step-fn)})
         (e/execute-steps)
         (e/execute-taps)
         (e/deliver-result)))))