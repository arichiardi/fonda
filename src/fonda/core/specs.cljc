(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::initial-ctx map?)

(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly (s/nilable fn?))

(s/def ::steps (s/coll-of ::step/step))

(s/def ::config
  (s/keys :opt-un [::anomaly? ::initial-ctx]))

(s/fdef fonda.core/execute
  :args (s/cat :config ::config
               :steps ::steps
               :on-exception ::on-exception
               :on-success ::on-success
               :on-anomaly (s/? ::on-anomaly)))
