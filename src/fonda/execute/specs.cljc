(ns fonda.execute.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

;; the following are all required but nilable we use a record as FondaContext.

(s/def ::anomaly-fn (s/nilable fn?))
(s/def ::exception (s/nilable #(instance? js/Error %)))
(s/def ::anomaly (s/nilable any?))
(s/def ::queue (s/coll-of ::step))
(s/def ::stack (s/coll-of ::step))

(s/def ::fonda-context-async some?)

(s/def ::fonda-context-sync
  (s/keys :req-un [::ctx
                   ::on-success
                   ::on-anomaly
                   ::on-exception
                   ::queue
                   ::stack
                   ::exception
                   ::anomaly
                   ::anomaly-fn]))

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

(s/fdef assoc-injector-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res (s/or :step ::step/step
                          :step-coll (s/coll-of ::step/step))))