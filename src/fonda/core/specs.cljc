(ns fonda.core.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.execute.specs :as execute]
            [fonda.step.specs :as step]))

;; handler-maps keys in fonda.core can be either strings or keywords
(s/def ::handlers-map (s/map-of ::execute/name fn?))

;; Config
(s/def ::anomaly? (s/or :boolean boolean? :predicate fn?))
(s/def ::mock-fns (s/map-of ::execute/str-or-kw fn?))
(s/def ::ctx map?)
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)
(s/def ::callbacks-wrapper-fn (s/nilable fn?))

(s/def ::config
  (s/keys :opt-un [::anomaly?
                   ::mock-fns
                   ::ctx
                   ::anomaly-handlers
                   ::exception-handlers
                   ::callbacks-wrapper-fn]))

(s/fdef fonda.core/execute
  :args (s/cat :config ::config
               :steps (s/spec (s/* ::execute/step))
               :on-exception ::execute/on-exception
               :on-success ::execute/on-success
               :on-anomaly (s/? ::execute/on-anomaly)))
