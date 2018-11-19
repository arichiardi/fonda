(ns fonda.execute
  (:require [fonda.async :as a]
            [fonda.runtime :as r]
            [fonda.step :as st]
            [cljs.spec.alpha :as s]))

(s/fdef assoc-tap-result
  :args (s/cat :fonda-ctx ::r/fonda-context
               :res any?))

(defn assoc-tap-result [{:as fonda-ctx :keys [anomaly?]} res]
  (if (anomaly? res)
    (assoc fonda-ctx :anomaly res)
    fonda-ctx))



(s/fdef assoc-processor-result
  :args (s/cat :fonda-ctx ::r/fonda-context
               :path ::st/path
               :res any?))

(defn assoc-processor-result
  [{:as fonda-ctx :keys [anomaly?]} path res]
  (if (anomaly? res)
    (assoc fonda-ctx :anomaly res)
    (assoc-in fonda-ctx (concat [:ctx] path) res)))


(s/fdef try-step
  :args (s/cat :fonda-ctx ::r/fonda-context
               :step ::st/step))

(defn- try-step
  "Tries running the given step (a tap step, or a processor step).
  If an exception gets triggerd, an exception is added on the context.
  If an anomaly is returned, an anomaly is added to the context"
  [{:as fonda-ctx :keys [ctx log-step-fn]}
   {:as step :keys [path name processor tap]}]
  (try
    (let [res (if processor (processor ctx) (tap ctx))
          assoc-result-fn (cond
                            (not (nil? tap)) (partial assoc-tap-result fonda-ctx)
                            (not (nil? processor)) (partial assoc-processor-result fonda-ctx path))
          assoc-result #(-> (assoc-result-fn %) (update :step-log log-step-fn step res))]

      (if (a/async? res)
        (a/continue res assoc-result #(assoc fonda-ctx :exception %))
        (assoc-result res)))

    (catch :default e
      (assoc fonda-ctx :exception e))))



(s/fdef try-global-tap
  :args (s/cat :tap-fn ::st/tap
               :fonda-ctx ::r/fonda-context))

(defn- try-logger
  "Runs a global tap function.
  If the tap function triggers any exception, adds an exception to the FondaContext record"
  [tap-fn fonda-ctx]
  (try
    (let [res (tap-fn fonda-ctx)]
      (if (a/async? res)
        (a/continue res (fn [_] fonda-ctx) #(assoc fonda-ctx :exception %))
        fonda-ctx))

    (catch :default e
      (assoc fonda-ctx :exception e))))



(s/fdef deliver-result
  :args (s/cat :fonda-ctx ::r/fonda-context))

(defn- deliver-result
  "Calls a callback depending on what is on the context.
  If there is an exception on the context, calls on-exception.
  If there is an anomaly on the context, calls on-anomaly.
  Otherwise calls on-success."
  [{:as fonda-ctx :keys [exception anomaly on-exception on-anomaly on-success ctx]}]
  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx deliver-result on-exception)
    (let [cb (cond exception #(on-exception exception)
                   anomaly #(on-anomaly anomaly)
                   :else #(on-success ctx))]
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
  :args (s/cat :fonda-ctx ::r/fonda-context))

(defn execute-loggers
  "Executes one of the global tap functions.

  If the context has an anomaly, calls log-anomaly.
  If the context has an exception, calls log-exception"
  [{:as fonda-ctx :keys [exception anomaly log-exception log-anomaly log-success]}]
  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx execute-loggers log-exception)

    (if-let [log-fn (cond
                      (and exception log-exception) log-exception
                      (and anomaly log-anomaly) log-anomaly
                      (and (not anomaly) (not exception) log-success) log-success
                      )]
      (try-logger log-fn fonda-ctx)
      fonda-ctx)))



(s/fdef execute-steps
  :args (s/cat :fonda-ctx ::r/fonda-context))

(defn execute-steps
  "Sequentially runs each of the steps.

  It blocks the execution on the asynchronous steps.  If any step assocs
  an exception to the FondaContext record, the execution stops."
  [{:as fonda-ctx :keys [queue exception anomaly]}]
  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx execute-steps)
    (let [step (peek queue)]
      ;;(println "step:" step)
      (if (or (not step) exception anomaly)
        fonda-ctx
        (recur
         (-> fonda-ctx
             (assoc :queue (pop queue))
             (try-step step)))))))
