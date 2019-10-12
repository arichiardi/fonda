(ns fonda.execute
  (:require [clojure.spec.alpha :as s]
            [fonda.async :as a]
            [fonda.step :as st]))

(defn assoc-tap-result
  [{:as fonda-ctx :keys [anomaly-fn :processor-results-stack]} res]
  (let [anomaly? (and anomaly-fn (anomaly-fn res))

        ;; taps only contribute anomalies to the context
        new-fonda-ctx (if anomaly? (assoc fonda-ctx :anomaly res) fonda-ctx)]

    (cond-> new-fonda-ctx

            ;; Only if the tap result is an anomaly, it adds the result to the results stack
            anomaly? (update :processor-results-stack conj res))))

(defn assoc-processor-result
  [{:as fonda-ctx :keys [anomaly-fn]} path res]
  (let [new-fonda-ctx (cond

                        ;; If it is an anomaly, associates the anomaly on the context, and execution will stop here
                        (and anomaly-fn (anomaly-fn res)) (assoc fonda-ctx :anomaly res)

                        ;; If there is no path on the step, it doesn't contribute to the context
                        (nil? path) fonda-ctx

                        ;; Otherwise associates the result into the context
                        :else (assoc-in fonda-ctx (concat [:ctx] path) res))]


    ;; It always adds the result ot the results stack
    (update new-fonda-ctx :processor-results-stack conj res)))

(defn assoc-injector-result
  [{:as fonda-ctx :keys [queue]} res]
  (let [steps (if (sequential? res) res [res])]
    (assoc fonda-ctx :queue (into #queue [] st/xf (concat steps queue)))))

(defn assoc-exception-result [fonda-ctx e]
  (-> fonda-ctx
      (assoc :exception e)
      (update :processor-results-stack conj e)))

(defn- try-step
  "Tries running the given step (a tap step, or a processor step).
  If an exception gets triggerd, an exception is added on the context.
  If an anomaly is returned, an anomaly is added to the context"
  [{:as fonda-ctx :keys [ctx mock-fns processor-results-stack]}
   {:as step :keys [processor tap inject name]}]
  (try
    (let [last-res (last processor-results-stack)

          ;; First step only gets the ctx, next ones receive last-result,ctx
          args (if (empty? processor-results-stack) [ctx] [last-res ctx])

          ; fn is an alias for processor
          processor (or processor (:fn step))

          ;; If there is a mocked-fn with the same name, it will used the mocked-fn instead
          mocked-fn (when name (get mock-fns (keyword name)))
          f (or mocked-fn processor tap inject)
          res (apply f args)
          assoc-result-fn (cond
                            tap (partial assoc-tap-result fonda-ctx)
                            processor (partial assoc-processor-result fonda-ctx (:path step))
                            inject (partial assoc-injector-result fonda-ctx))]
      (if (a/async? res)
        (a/continue res assoc-result-fn #(assoc-exception-result fonda-ctx %))
        (assoc-result-fn res)))

    (catch :default e
      (assoc-exception-result fonda-ctx e))))

(defn- deliver-result
  "Calls a callback depending on what is on the context.
  If there is an exception on the context, calls on-exception.
  If there is an anomaly on the context, calls on-anomaly.
  Otherwise calls on-success."
  [{:as fonda-ctx :keys [ctx exception anomaly processor-results-stack]}]
  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx deliver-result (:on-exception fonda-ctx))
    (let [[cb result] (cond exception [(:on-exception fonda-ctx) exception]
                            anomaly [(:on-anomaly fonda-ctx) anomaly]
                            :else [(:on-success fonda-ctx) ctx])]
      (cb (last processor-results-stack) result))))

(defn execute-steps
  "Sequentially runs each of the steps.

  It blocks the execution on the asynchronous steps.  If any step assocs
  an exception to the FondaContext record, the execution stops."
  [{:as fonda-ctx :keys [queue stack exception anomaly]}]
  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx execute-steps)
    (let [step (peek queue)]
      (if (or (not step) exception anomaly)
        fonda-ctx
        (recur
          (-> fonda-ctx
              (assoc :queue (pop queue))
              (assoc :stack (conj stack step))
              (try-step step)))))))

(defn execute-handlers
  "Executes the anomaly and exception handlers.

  If the context has an anomaly, calls the anomaly handler of the step that returned the anomaly.
  If the context has an exception, calls the exception handler of the step that triggered the exception"
  [{:as fonda-ctx :keys [ctx exception anomaly exception-handlers anomaly-handlers stack]}]

  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx execute-handlers)
    (let [[handlers arg] (cond
                           anomaly [anomaly-handlers {:ctx ctx :anomaly anomaly}]
                           exception [exception-handlers {:ctx ctx :exception exception}])
          handler-fn (get handlers (-> stack last :name))]
      (when handler-fn (handler-fn arg))
      fonda-ctx)))

(defrecord FondaContext
  [;; A function that gets a map and determines if it is an anomaly
   anomaly-fn

   ;; The context data that gets passed to the step functions
   ctx

   ;; The exception thrown by the latest failing step
   exception

   ;; The anomaly returned by the latest failing step
   anomaly

   ;; A map of functions indexed by step name that get called with a map `{:ctx <ctx> :anomaly <anomaly>}` when the step
   ;; returns an anomaly.
   anomaly-handlers

   ;; A map of functions indexed by step name that get called with a map `{:ctx <ctx> :exception <exception>}` when the
   ;; step triggers an exception.
   exception-handlers

   ;; Callback function that gets called with the context after all the steps succeeded
   on-success

   ;; Callback function that gets called with an exception that a step triggered
   on-exception

   ;;A map of functions that will replace the function on the step, matching the map key with the step name
   mock-fns

   ;; Callback function taht gets called with an anomaly that a step returned
   on-anomaly

   ;; The steps that haven't been already processed
   queue

   ;; The steps that have already been processed
   stack

   ;; The results of the steps that have already been processed
   processor-results-stack
   ])

(defn cognitect-anomaly?
  [m]
  (:cognitect.anomalies/anomaly m))

(defn anomaly-fn
  "Compute the anomaly-fn from the parameter.

  This function does the :anomaly? parameter validation."
  [anomaly?]
  ;; use specs one day?
  (assert (or (boolean? anomaly?)
              (nil? anomaly?)
              (fn? anomaly?))
          "The :anomaly? key should be either nil, boolean or a function.")

  (cond
    (true? anomaly?) cognitect-anomaly?
    (false? anomaly?) nil
    (some? anomaly?) anomaly?
    :else nil))

(defn fonda-context
  "Build the run time \"Fonda\" context from the config.

  This function does config validation."
  [config]
  (let [{:keys [anomaly? mock-fns ctx anomaly-handlers exception-handlers on-exception on-anomaly on-success steps]} config
        anomaly-fn (anomaly-fn anomaly?)]
    (assert (or (not anomaly-fn) (and anomaly-fn on-anomaly)) "When :anomaly? is truthy the on-anomaly callback is required.")
    (assert on-success "The on-success callback is required.")
    (assert on-exception "The on-exception callback is required.")

    (map->FondaContext
      (merge {:anomaly-handlers   (clojure.walk/keywordize-keys anomaly-handlers)
              :exception-handlers (clojure.walk/keywordize-keys exception-handlers)
              :on-anomaly         on-anomaly
              :on-exception       on-exception
              :on-success         on-success
              :mock-fns           (clojure.walk/keywordize-keys (or mock-fns {}))}
             (when anomaly-fn
               {:anomaly-fn anomaly-fn})
             {:ctx                     (or ctx {})
              :queue                   (into #queue [] st/xf steps)
              :stack                   []
              :processor-results-stack []}))))