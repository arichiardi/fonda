(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

(s/def ::anomaly? (s/nilable fn?))
(s/def ::log-exception (s/nilable fn?))
(s/def ::log-anomaly (s/nilable fn?))
(s/def ::log-success (s/nilable fn?))

(s/def ::initial-ctx map?) ;; part of the config

(s/def ::exception (s/nilable #(instance? js/Error %)))
(s/def ::anomaly (s/nilable any?))
(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly fn?)

(s/def ::step
  (s/or :tap-step ::step/tap-step
        :processor-step ::step/processor-step))

(s/def ::steps (s/coll-of ::step/step))

(s/def ::config
  (s/keys :opt-un [::anomaly?
                   ::log-exception
                   ::log-anomaly
                   ::initial-ctx]))

(s/fdef execute
  :args (s/cat :config ::config
               :steps ::steps
               :on-success ::on-success
               :on-anomaly ::on-anomaly
               :on-exception ::on-exception))
