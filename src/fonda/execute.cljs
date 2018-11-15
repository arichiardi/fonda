(ns fonda.execute
  (:require [fonda.async :as a]
            [fonda.runtime :as r]
            [fonda.step :as st]
            [cljs.spec.alpha :as s]))

(s/fdef set-tap-result
  :args (s/cat :runtime-ctx ::r/runtime-context))

(defn set-tap-result [{:as runtime-ctx :keys [anomaly?]} res]
  (if (anomaly? res)
    (assoc runtime-ctx :anomaly res)
    runtime-ctx))



(s/fdef set-processor-result
  :args (s/cat :runtime-ctx ::r/runtime-context
               :path ::st/path
               :res any?))

(defn set-processor-result
  [{:as runtime-ctx :keys [anomaly?]} path res]
  (if (anomaly? res)
    (assoc runtime-ctx :anomaly res)
    (assoc-in runtime-ctx (concat [:ctx] path) res)))



(s/fdef try-step
  :args (s/cat :runtime-ctx ::r/runtime-context
               :step ::st/step))

(defn- try-step
  "Tries running the given step (a tap step, or a processor step).
  If an exception gets triggerd, an error is added on the context.
  If an anomaly is returned, an anomaly is added to the context"
  [{:as runtime-ctx :keys [ctx log-step-fn]}
                 {:as step :keys [path name processor tap]}]
  (try
    (let [res (if processor (processor ctx) (tap runtime-ctx))
          set-result-fn (cond
                          (not (nil? tap)) (partial set-tap-result runtime-ctx)
                          (not (nil? processor)) (partial set-processor-result runtime-ctx path))
          set-result #(-> (set-result-fn %) (update :step-log log-step-fn step res))]

      (if (a/async? res)
        (a/continue res set-result #(assoc runtime-ctx :error %))
        (set-result res)))

    (catch :default e
      (assoc runtime-ctx :error e))))



(s/fdef try-global-tap
  :args (s/cat :tap-fn ::st/tap
               :runtime-ctx ::r/runtime-context))

(defn- try-global-tap
  "Runs a global tap function.
  If the tap function triggers any exception, adds an error on the context"
  [tap-fn runtime-ctx]
  (try
    (let [res (tap-fn runtime-ctx)]
      (if (a/async? res)
        (a/continue res (fn [_] runtime-ctx) #(assoc runtime-ctx :error %))
        runtime-ctx))

    (catch :default e
      (assoc runtime-ctx :error e))))



(s/fdef deliver-result
  :args (s/cat :runtime-ctx ::r/runtime-context))

(defn- deliver-result
  "Calls a callback depending on what is on the context.
  If there is an exception on the context, calls on-exception.
  If there is an anomaly on the context, calls on-anomaly.
  Otherwise calls on-complete."
  [{:as runtime-ctx :keys [error anomaly on-exception on-anomaly on-complete ctx]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx deliver-result on-exception)
    (let [cb (cond error #(on-exception error)
                   anomaly #(on-anomaly anomaly)
                   :else #(on-complete ctx))]
      (cb))))



(s/fdef default-log-step-fn
  :args (s/cat :step-log ::r/step-log
               :step ::st/step
               :result any?))

(defn- default-log-step-fn
  "Default function for adding step information on the log"
  [step-log step result]
  (conj step-log (:name step)))



;;;;;;;;;;;;
;; PUBLIC ;;
;;;;;;;;;;;;
(s/fdef execute-taps
  :args (s/cat :runtime-ctx ::r/runtime-context))

(defn execute-taps
  "Executes one of the global tap functions.
  If the context has an anomaly, calls anomaly-tap.
  If the context has an error, calls exception-tap"
  [{:as runtime-ctx :keys [error anomaly exception-tap anomaly-tap]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx execute-taps exception-tap)

    (if-let [tap-fn (cond
                      (and error exception-tap) exception-tap
                      (and anomaly anomaly-tap) anomaly-tap)]
      (try-global-tap tap-fn runtime-ctx)
      runtime-ctx)))



(s/fdef execute-steps
  :args (s/cat :runtime-ctx ::r/runtime-context))

(defn execute-steps
  "Sequentially runs each of the steps.
  It blocks the execution on the asynchronous steps.
  If any step assocs an error on the context, the execution stops."
  [{:as  runtime-ctx :keys [queue error anomaly]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx execute-steps)
    (let [step (peek queue)]
      ;;(println "step:" step)
      (if (or (not step) error anomaly)
        runtime-ctx
        (recur
          (-> runtime-ctx
              (assoc :queue (pop queue))
              (try-step step)))))))