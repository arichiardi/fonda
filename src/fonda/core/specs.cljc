(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::initial-ctx map?)

(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly (s/nilable fn?))

(s/def ::step
  (s/or :tap-step       ::step/tap-step
        :processor-step ::step/processor-step
        :injector-step  ::step/injector-step))

(s/def ::steps (s/coll-of ::step))

(s/def ::config
  (s/keys :opt-un [::anomaly? ::initial-ctx]))

(s/fdef fonda.core/execute
  :args (s/cat :config ::config
               :steps ::steps
               :on-exception ::on-exception
               :on-success ::on-success
               :on-anomaly (s/? ::on-anomaly)))
