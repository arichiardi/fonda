(ns fonda.execute.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]
            [fonda.core.specs :as core]))

(s/def ::js-error #(instance? js/Error %))

;; this namespace has a different take on the step name, we duplicate waiting
;; for spec2 to save us
(s/def ::name (s/nilable keyword?))

(s/def ::step-name-map
  (s/keys :opt-un [::name]))

(s/def ::step
  (s/or :tap (s/merge ::step/tap-step ::step-name-map)
        :processor (s/merge ::step/processor-step ::step-name-map)
        :injector (s/merge ::step/injector-step ::step-name-map)))

(s/def ::steps (s/coll-of ::step))

;; handler-maps keys in fonda.execute can only be keywords
(s/def ::handlers-map (s/nilable (s/map-of ::step/name fn?)))
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)

(s/def ::queue ::steps)
(s/def ::stack ::steps)

;; the following are all required but nilable we use a record as FondaContext.
(s/def ::anomaly-fn (s/nilable fn?))
(s/def ::exception (s/nilable ::js-error))
(s/def ::anomaly (s/nilable any?))

(s/def ::fonda-context-async some?)

(s/def ::fonda-context-sync
  (s/keys :req-un [::core/ctx
                   ::core/on-success
                   ::core/on-exception
                   ::queue
                   ::stack]
          :opt-un [::anomaly-handlers
                   ::exception-handlers
                   ::core/on-anomaly
                   ::anomaly-fn
                   ::exception
                   ::anomaly]))

(s/def ::fonda-context (s/or :async ::fonda-context-async
                             :sync ::fonda-context-sync))

(s/fdef fonda.execute/execute-steps
  :args (s/cat :fonda-ctx ::fonda-context))

(s/fdef fonda.execute/execute-handlers
  :args (s/cat :fonda-ctx ::fonda-context))

(s/fdef fonda.execute/deliver-result
  :args (s/cat :fonda-ctx ::fonda-context))

(s/fdef fonda.execute/try-step
  :args (s/cat :fonda-ctx ::fonda-context
               :step ::step))

(s/fdef fonda.execute/assoc-processor-result
  :args (s/cat :fonda-ctx ::fonda-context
               :path ::step/path
               :res any?))

(s/fdef fonda.execute/assoc-tap-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res any?))

(s/fdef fonda.execute/assoc-injector-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res (s/or :step ::core/step :steps ::core/steps)))
