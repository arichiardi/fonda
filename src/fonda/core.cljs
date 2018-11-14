(ns fonda.core
  (:require [fonda.async :as a]
            [fonda.anomaly]))


(defrecord FondaContext [anomaly? exception-tap anomaly-tap ctx error anomaly on-complete on-exception on-anomaly queue step-log log-step-fn])

(defn set-tap-result [{:as runtime-ctx :keys [anomaly?]} res]
  (if (anomaly? res)
    (assoc runtime-ctx :anomaly res)
    runtime-ctx))

(defn set-resolver-result [{:as runtime-ctx :keys [anomaly?]} path res]
  (if (anomaly? res)
    (assoc runtime-ctx :anomaly res)
    (assoc-in runtime-ctx (concat [:ctx] path) res)))

(defn- try-step [{:as runtime-ctx :keys [ctx log-step-fn]}
                 {:as step :keys [path name resolver tap]}]
  (try
    (let [res (if resolver (resolver ctx) (tap runtime-ctx))
          set-result-fn (cond
                          (not (nil? tap)) (partial set-tap-result runtime-ctx)
                          (not (nil? resolver)) (partial set-resolver-result runtime-ctx path))
          set-result #(-> (set-result-fn %) (update :step-log log-step-fn step res))]

      (if (a/async? res)
        (a/continue res set-result #(assoc runtime-ctx :error %))
        (set-result res)))

    (catch :default e
      (assoc runtime-ctx :error e))))


(defn- try-global-tap [tap-fn runtime-ctx]
  (try
    (let [res (tap-fn runtime-ctx)]
      (if (a/async? res)
        (a/continue res (fn [_] runtime-ctx) #(assoc runtime-ctx :error %))
        runtime-ctx))

    (catch :default e
      (assoc runtime-ctx :error e))))


(defn- deliver-result [{:as runtime-ctx :keys [error anomaly on-exception on-anomaly on-complete ctx]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx deliver-result on-exception)
    (let [cb (cond error #(on-exception error)
                   anomaly #(on-anomaly anomaly)
                   :else #(on-complete ctx))]

      (cb))))


(defn execute-taps [{:as runtime-ctx :keys [error anomaly exception-tap anomaly-tap]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx execute-taps exception-tap)

    (if-let [tap-fn (cond
                      (and error exception-tap) exception-tap
                      (and anomaly anomaly-tap) anomaly-tap)]
      (try-global-tap tap-fn runtime-ctx)
      runtime-ctx)))

(defn- process-chain [{:as   runtime-ctx
                       :keys [queue error anomaly]}]
  (if (a/async? runtime-ctx)
    (a/continue runtime-ctx process-chain)
    (let [step (peek queue)]
      ;;(println "step:" step)
      (if (or (not step) error anomaly)
        runtime-ctx
        (recur
          (-> runtime-ctx
              (assoc :queue (pop queue))
              (try-step step)))))))

(defn- default-log-step-fn [step-log step result]
  (conj step-log (:name step)))

;;;;;;;;;;;;;;;;;
;; Public API: ;;
;;;;;;;;;;;;;;;;;

(defn execute
  ([config steps ctx-seed on-complete on-anomaly on-exception]
   (let [{:keys [anomaly?
                 exception-tap
                 anomaly-tap
                 log-step-fn]} config]
     (-> (map->FondaContext {:anomaly?      (or anomaly? fonda.anomaly/anomaly?)
                             :exception-tap exception-tap
                             :anomaly-tap   anomaly-tap
                             :ctx           (or ctx-seed nil)
                             :on-complete   on-complete
                             :on-anomaly    on-anomaly
                             :on-exception  on-exception
                             :queue         (into #queue[] steps)
                             :step-log      []
                             :log-step-fn         (or log-step-fn default-log-step-fn)})
         (process-chain)
         (execute-taps)
         (deliver-result)))))


;;(replace-steps steps {:get-whaterver {:path [] :resolver (fn []) :tap (fn [])}})
;;(replace-resolvers steps {:step-name (fn [])})