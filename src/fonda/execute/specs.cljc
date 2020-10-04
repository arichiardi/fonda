(ns fonda.execute.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

(s/def ::js-error #(instance? js/Error %))

(s/def ::str-or-kw (s/or :string string? :keyword keyword?))
(s/def ::name (s/nilable ::str-or-kw))

(s/def ::step-name-map
  (s/keys :opt-un [::name]))

(s/def ::step
  (s/or :tap (s/merge ::step/tap-step ::step-name-map)
        :processor (s/merge ::step/processor-step ::step-name-map)
        :injector (s/merge ::step/injector-step ::step-name-map)))

;; handler-maps keys in fonda.execute can only be keywords
(s/def ::handlers-map (s/nilable (s/map-of keyword? fn?)))
(s/def ::anomaly-handlers ::handlers-map)
(s/def ::exception-handlers ::handlers-map)

(s/def ::queue (s/* ::step))
(s/def ::stack (s/* ::step))

;; the following are all required but nilable we use a record as FondaContext.
(s/def ::anomaly-fn (s/nilable fn?))
(s/def ::exception (s/nilable ::js-error))
(s/def ::anomaly (s/nilable any?))

(s/def ::on-success some?)
(s/def ::on-exception some?)
(s/def ::on-anomaly (s/nilable any?))

(s/def ::fonda-context-async some?)

(s/def ::fonda-context-sync
  (s/keys :req-un [::ctx
                   ::on-success
                   ::on-exception
                   ::queue
                   ::stack]
          :opt-un [::anomaly-handlers
                   ::exception-handlers
                   ::on-anomaly
                   ::anomaly-fn
                   ::exception
                   ::anomaly]))

(s/def ::fonda-context (s/or :async ::fonda-context-async
                             :sync ::fonda-context-sync))

(s/def ::config
  (s/keys :req-un [::step/steps
                   ::on-success
                   ::on-exception
                   ::on-anomaly]
          :opt-un [::anomaly?
                   ::mock-fns
                   ::ctx
                   ::anomaly-handlers
                   ::exception-handlers
                   ::callbacks-wrapper-fn]))

(s/fdef fonda.execute/fonda-context
  :args (s/cat :config ::config))

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
               :step ::step/processor-step
               :res any?))

(s/fdef fonda.execute/assoc-tap-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res any?))

(s/def ::injected-steps
  (s/or :step ::step ::step-seq (s/* ::step)))

(s/fdef fonda.execute/assoc-injector-result
  :args (s/cat :fonda-ctx ::fonda-context
               :res ::injected-steps))
