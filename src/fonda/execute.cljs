(ns fonda.execute
  (:require [clojure.spec.alpha :as s]
            [fonda.async :as a]
            [fonda.step :as st]))

(defn get-callback-fn
  [{:as fonda-ctx :keys [callbacks-wrapper-fn]} callback-val-or-fn]

  (cond

    ;; If the given callback is a function, calls it. It won't be wrapped
    (fn? callback-val-or-fn) callback-val-or-fn

    ;; If the given callback is a value, and there is a wrapper function, wraps it
    (fn? callbacks-wrapper-fn) (partial callbacks-wrapper-fn callback-val-or-fn)

    :else
    callback-val-or-fn))

(defn assoc-tap-result
  [{:as fonda-ctx :keys [anomaly-fn :processor-results-stack]} res]
  (let [anomaly? (and anomaly-fn (anomaly-fn res))

        ;; taps only contribute anomalies to the context
        new-fonda-ctx (if anomaly? (assoc fonda-ctx :anomaly res) fonda-ctx)]

    (cond-> new-fonda-ctx

            ;; Only if the tap result is an anomaly, it adds the result to the results stack
            anomaly? (update :processor-results-stack conj res))))

(defn assoc-processor-result
  [{:as fonda-ctx :keys [anomaly-fn]}
   {:keys [path is-anomaly-error?]}
   res]
  (let [new-fonda-ctx (cond

                        ;; If it is an anomaly, associates the anomaly on the context, and execution will stop here
                        (and anomaly-fn (anomaly-fn res) (is-anomaly-error? res)) (assoc fonda-ctx :anomaly res)

                        ;; If there is no path on the step, it doesn't contribute to the context
                        (nil? path) fonda-ctx

                        ;; Otherwise associates the result into the context
                        :else (assoc-in fonda-ctx (concat [:ctx] path) res))]


    ;; It always adds the result ot the results stack
    (update new-fonda-ctx :processor-results-stack conj res)))

(defn assoc-injector-result
  [{:as fonda-ctx :keys [queue]} res]
  (let [steps (cond
                (nil? res) []
                (sequential? res) res
                :else [res])]
    (assoc fonda-ctx :queue (into #queue [] st/xf (concat steps queue)))))

(defn assoc-exception-result [fonda-ctx e]
  (-> fonda-ctx
      (assoc :exception e)
      (update :processor-results-stack conj e)))

(defn handle-exception
  [{:as fonda-ctx :keys [ctx]} {:keys [on-error] :as step} e]
  (when on-error
    ((get-callback-fn fonda-ctx on-error) ctx e))
  (assoc-exception-result fonda-ctx e))

(defn invoke-post-callback-fns
  [{:as fonda-ctx :keys [anomaly-fn ctx]}
   {:keys [on-complete on-success on-error path is-anomaly-error?] :as step}
   step-res]
  (let [aug-ctx (if path (assoc-in ctx path step-res) ctx)]

    ;; Always calls on-complete
    (when on-complete
      ((get-callback-fn fonda-ctx on-complete) aug-ctx step-res))

    (if (and anomaly-fn (anomaly-fn step-res) (is-anomaly-error? step-res))

      ;; If anomaly, calls on-error
      (when on-error
        ((get-callback-fn fonda-ctx on-error) aug-ctx step-res))

      ;; Otherwise calls on-success
      (when on-success
        ((get-callback-fn fonda-ctx on-success) aug-ctx step-res)))))

(defn- try-step
  "Tries running the given step (a tap step, or a processor step).
  If an exception gets triggerd, an exception is added on the context.
  If an anomaly is returned, an anomaly is added to the context"
  [{:as fonda-ctx :keys [ctx mock-fns processor-results-stack]}
   {:as step :keys [processor fn tap inject name on-start]}]

  ;; Calls the on-start callback with the context
  (when on-start
    ((get-callback-fn fonda-ctx on-start) ctx))

  (let [mocked-fn (when name (get mock-fns (keyword name))) ;; we use a mocked-fn with the same name if there
        f (or mocked-fn processor fn tap inject)
        assoc-result-fn #(do
                           (invoke-post-callback-fns fonda-ctx step %)
                           (cond
                             tap (assoc-tap-result fonda-ctx %)
                             (or processor fn) (assoc-processor-result fonda-ctx step %)
                             inject (assoc-injector-result fonda-ctx %)))]
    (try
      (let [res (f ctx)]
        (if (a/async? res)
          (a/continue res assoc-result-fn #(handle-exception fonda-ctx step %))
          (assoc-result-fn res)))
      (catch :default e
        (handle-exception fonda-ctx step e)))))

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
                            :else [(:on-success fonda-ctx) (last processor-results-stack)])]
      ((get-callback-fn fonda-ctx cb) ctx result))))

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

   ;; A function with the signature (fn [on-callback-val step-res]) that, if provided, will be called on every
   ;; callback (steps and globals). When provided, the callbacks can have any value - a function is not required anymore-
   ;; and that value will be  passed to this function.
   ;; Useful if you want to dispatch events instead of calling functions.
   callbacks-wrapper-fn

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
  (let [{:keys [anomaly? mock-fns ctx anomaly-handlers exception-handlers callbacks-wrapper-fn on-exception on-anomaly on-success steps]} config
        anomaly-fn (anomaly-fn anomaly?)]
    (assert (or (not anomaly-fn) (and anomaly-fn on-anomaly)) "When :anomaly? is truthy the on-anomaly callback is required.")
    (assert on-success "The on-success callback is required.")
    (assert on-exception "The on-exception callback is required.")

    (map->FondaContext
      (merge {:anomaly-handlers     (clojure.walk/keywordize-keys anomaly-handlers)
              :exception-handlers   (clojure.walk/keywordize-keys exception-handlers)
              :callbacks-wrapper-fn callbacks-wrapper-fn
              :on-anomaly           on-anomaly
              :on-exception         on-exception
              :on-success           on-success
              :mock-fns             (clojure.walk/keywordize-keys (or mock-fns {}))}
             (when anomaly-fn
               {:anomaly-fn anomaly-fn})
             {:ctx                     (or ctx {})
              :queue                   (into #queue [] st/xf steps)
              :stack                   []
              :processor-results-stack []}))))