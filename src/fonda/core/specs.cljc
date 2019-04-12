(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

(s/def ::name (s/nilable (s/or :string string?
                               :keyword keyword?)))

;; handler-maps keys in fonda.core can be either strings or keywords
(s/def ::handlers-map (s/map-of ::name fn?))

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::initial-ctx map?)
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)

(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly (s/nilable fn?))

(s/def ::step-name-map
  (s/keys :opt-un [::name]))

(s/def ::step
  (s/or :tap (s/merge ::step/tap-step ::step-name-map)
        :processor (s/merge ::step/processor-step ::step-name-map)
        :injector (s/merge ::step/injector-step ::step-name-map)))

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
