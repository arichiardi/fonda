(ns fonda.core
  (:require [fonda.async :as a])
  (:refer-clojure :exclude [iter]))


(defrecord FondaContext [anomaly? exception-tap anomaly-tap ctx error anomaly on-complete on-error on-anomaly queue step-log])


(defn set-tap-result [{:as fonda-context :keys [anomaly?]} res]
  (if (anomaly? res)
    (assoc fonda-context :anomaly res)
    fonda-context))

(defn- try-tap [{:as fonda-context :keys [ctx step-log]}
                {:as step :keys [name tap]}]
  (try
    (let [res (tap ctx)
          set-result (partial set-tap-result fonda-context)]
      (if (a/async? res)
        (a/continue res set-result)
        (set-result res)))
    (catch :default e
      (assoc fonda-context :error e))))

(defn set-resolver-result [{:as fonda-context :keys [anomaly?]} path res]
  (if (anomaly? res)
    (assoc fonda-context :anomaly res)
    (assoc-in fonda-context (concat [:ctx] path) res)))

(defn- try-resolver [{:as fonda-context :keys [ctx step-log]}
                 {:as step :keys [path name resolver]}]
  (try
    (let [res (resolver ctx)
          set-result (partial set-resolver-result fonda-context path)]
      (if (a/async? res)
        (a/continue res set-result)
        (set-result res)))
    (catch :default e
      (assoc fonda-context :error e))))

(defn- try-step [fonda-context {:as step :keys [tap resolver]}]
  (cond
    (not (nil? tap)) (try-tap fonda-context step)
    (not (nil? resolver)) (try-resolver fonda-context step)))

(defn- process-chain [{:as fonda-context
                       :keys [queue error anomaly]}]
  (if (a/async? fonda-context)
    (a/continue fonda-context process-chain)
    (let [step (peek queue)]
      (if (or (not step) error anomaly))
      (recur
        (-> fonda-context
            (assoc :queue (pop queue))
            (try-step step))))))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;

(defn execute
  ([config steps on-complete on-anomaly on-error]
   (let [{:keys [anomaly?
                 exception-tap
                 anomaly-tap
                 ctx-seed]} config]
     (-> (map->FondaContext {:anomaly?      anomaly?
                             :exception-tap exception-tap
                             :anomaly-tap   anomaly-tap
                             :ctx           (or ctx-seed {})
                             :on-complete   on-complete
                             :on-anomaly    on-anomaly
                             :queue         steps
                             :step-log      {}})
         (process-chain)))

    ))
