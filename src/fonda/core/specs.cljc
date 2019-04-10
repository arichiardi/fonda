(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

(s/def ::ctx map?)

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::initial-ctx ::ctx)

(s/def ::handlers-map (s/map-of ::step/name fn?))
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)

(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly (s/nilable fn?))

(s/def ::step
  (s/or :tap-step       ::step/tap-step
        :processor-step ::step/processor-step
        :injector-step  ::step/injector-step))

(s/def ::steps (s/coll-of ::step))

(s/def ::config
  (s/keys :opt-un [::anomaly?
                   ::initial-ctx
                   ::anomaly-handlers
                   ::exception-handlers]))

(s/fdef fonda.core/execute
  :args (s/cat :config ::config
               :steps ::steps
               :on-exception ::on-exception
               :on-success ::on-success
               :on-anomaly (s/? ::on-anomaly)))
