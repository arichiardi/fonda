(ns fonda.core
  #?(:cljs (:refer-clojure :exclude [iter]))
  (:require [fonda.async :as a])
  )

(defrecord Context [error queue on-complete on-error on-anomaly])

(defn- try-f [ctx f]
  (if f
    (try
      (f ctx)
      (catch #?(:clj Exception :cljs :default) e
        (assoc ctx :error e)))
    ctx))

(defn- context
  ([request queue]
   (new Context request nil nil queue nil nil nil))
  ([request queue on-complete on-error]
   (new Context request nil nil queue nil on-complete on-error)))

;;
;; Public API:
;;

(defn execute
  ([config input on-complete on-anomaly on-error]

   ))
