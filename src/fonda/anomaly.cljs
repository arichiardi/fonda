(ns fonda.anomaly)

(defn anomaly? [m]
  (some? (get m :cognitect.anomalies/anomaly)))