(ns fonda.anomaly)

(def anomaly-key :cognitect.anomalies/anomaly)

(defn anomaly? [m]
  (some? (get m anomaly-key)))