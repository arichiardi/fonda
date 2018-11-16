(ns fonda.log
  (:require [cljs.spec.alpha :as s]
            [fonda.runtime :as r]))

(defn- println-err
  [& args]
  (binding [*print-fn* *print-err-fn*]
    (apply println args)))

(s/fdef default-log-exception
        :args (s/cat :runtime-ctx ::r/runtime-context-sync))

(defn default-log-exception [{:keys [ctx step-log exception]}]
  (println-err "Fonda found an exception:" exception)
  (println-err "ctx:" ctx)
  (println-err "step-log:" step-log))

(defn default-log-anomaly [{:keys [ctx step-log anomaly]}]
  (println-err "Fonda found an anomaly:" anomaly)
  (println-err "ctx:" ctx)
  (println-err "step-log:" step-log))