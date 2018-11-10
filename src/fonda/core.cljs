(ns fonda.core
  (:require [fonda.async :as a])
  (:refer-clojure :exclude [iter]))


(defrecord Context [error anomaly step-log on-complete on-error on-anomaly])

(defn- try-f [ctx f]
  (if f
    (try
      (f ctx)
      (catch :default e
        (assoc ctx :error e)))
    ctx))

(defn- context
  [initial-ctx on-complete on-error on-anomaly]
  #_(new Context  nil nil [] on-complete on-error on-anomaly))

;;
;; Public API:
;;

(defn execute
  ([config steps on-complete on-anomaly on-error]
   ))
