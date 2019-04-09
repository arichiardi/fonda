(ns fonda.execute-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [fonda.execute :as execute]
            [fonda.execute.specs]
            [orchestra-cljs.spec.test :as orchestra]))

(orchestra/instrument)

(deftest anomaly-fn-tests
  (let [anomaly-fn (execute/anomaly-fn true)]
    (is (some? (anomaly-fn {:cognitect.anomalies/anomaly {}})) "it should return the cognitect anomaly predicate when true"))

  (is (nil? (execute/anomaly-fn nil)) "it should return the nil when nil")
  (is (nil? (execute/anomaly-fn false)) "it should return the nil when false")

  (let [anomaly-fn (execute/anomaly-fn
                    (fn [m]
                      (::anomaly m)))]
    (is (some? (anomaly-fn {::anomaly :foo})) "it should return the custom anomaly predicate when set"))

  (is (thrown? js/Error (execute/anomaly-fn "what?") "it should throw if passed something that is not a function")))
