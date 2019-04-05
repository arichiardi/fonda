(ns fonda.execute
  (:require [clojure.spec.alpha :as s]
            [fonda.async :as a]
            [fonda.step :as st]))

(def log-map-keys [:exception :anomaly :ctx :stack])

(defn assoc-tap-result [{:as fonda-ctx :keys [anomaly?]} res]
  (if (anomaly? res)
    (assoc fonda-ctx :anomaly res)
    fonda-ctx))

(defn assoc-processor-result
  [{:as fonda-ctx :keys [anomaly?]} path res]
  (if (anomaly? res)
    (assoc fonda-ctx :anomaly res)
    (assoc-in fonda-ctx (concat [:ctx] path) res)))

(defn- try-step
  "Tries running the given step (a tap step, or a processor step).
  If an exception gets triggerd, an exception is added on the context.
  If an anomaly is returned, an anomaly is added to the context"
  [{:as fonda-ctx :keys [ctx]}
   {:as step :keys [path name processor tap]}]
  (try
    (let [res (if processor (processor ctx) (tap ctx))
          assoc-result-fn (cond
                            (not (nil? tap)) (partial assoc-tap-result fonda-ctx)
                            (not (nil? processor)) (partial assoc-processor-result fonda-ctx path))]

      (if (a/async? res)
        (a/continue res assoc-result-fn #(assoc fonda-ctx :exception %))
        (assoc-result-fn res)))

    (catch :default e
      (assoc fonda-ctx :exception e))))

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

;;;;;;;;;;;;
;; PUBLIC ;;
;;;;;;;;;;;;

(defn execute-loggers
  "Executes one of the global tap functions.

  If the context has an anomaly, calls log-anomaly.
  If the context has an exception, calls log-exception"
  [{:as fonda-ctx :keys [exception anomaly log-exception log-anomaly log-success]}]

  (if (a/async? fonda-ctx)
    (a/continue fonda-ctx execute-loggers log-exception)

    (let [log-fn (cond (and exception log-exception) log-exception
                       (and anomaly log-anomaly) log-anomaly
                       (and (not anomaly) (not exception) log-success) log-success)]
      (when log-fn (log-fn (select-keys fonda-ctx log-map-keys)))
      fonda-ctx)))

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

(defrecord FondaContext
  [;; A function that gets a map and determines if it is an anomaly
   anomaly?

   ;; A function gets called with the fonda context when there is an exception
   log-exception

   ;; A function that gets called with the fonda context when a step returns an anomaly
   log-anomaly

   ;; A function that gets called with the fonda context after all steps succeeded
   log-success

   ;; The context data that gets passed to the step functions
   ctx

   ;; An exception thrown by a step
   exception

   ;; An anomaly returned by a step
   anomaly

   ;; Callback function that gets called with the context after all the steps succeeded
   on-success

   ;; Callback function that gets called with an exception that a step triggered
   on-exception

   ;; Callback function taht gets called with an anomaly that a step returned
   on-anomaly

   ;; The steps that haven't been already processed
   queue

   ;; The steps that have already been processed
   stack])
