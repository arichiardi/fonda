(ns fonda.execute.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

(s/def ::queue (s/coll-of ::step))
(s/def ::stack (s/coll-of ::step))

(s/def ::fonda-context-async a/async?)
(s/def ::fonda-context-sync
  (s/keys :req-un [::anomaly?
                   ::ctx
                   ::on-success
                   ::on-anomaly
                   ::on-exception
                   ::queue
                   ::stack
                   ::exception
                   ::anomaly]))

(s/def ::fonda-context (s/or :async ::fonda-context-async
                             :sync ::fonda-context-sync))

(s/fdef execute-steps
  :args (s/cat :fonda-ctx ::fonda-context))

(s/fdef deliver-result
  :args (s/cat :fonda-ctx ::fonda-context))

(s/fdef try-step
  :args (s/cat :fonda-ctx ::fonda-context
               :step ::step/step))

(s/fdef assoc-processor-result
  :args (s/cat :fonda-ctx ::fonda-context
               :path ::step/path
               :res any?))

(s/fdef assoc-tap-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res any?))
