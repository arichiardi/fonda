(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.core.step.specs :as core-step]))

(s/def ::ctx map?)

;; handler-maps keys in fonda.core can be either strings or keywords
(s/def ::handlers-map (s/map-of ::core-step/name fn?))

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::initial-ctx ::ctx)
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)

(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly (s/nilable fn?))

(s/def ::config
  (s/keys :opt-un [::anomaly?
                   ::initial-ctx
                   ::anomaly-handlers
                   ::exception-handlers]))

(s/fdef fonda.core/execute
  :args (s/cat :config ::config
               :steps ::core-step/steps
               :on-exception ::on-exception
               :on-success ::on-success
               :on-anomaly (s/? ::on-anomaly)))