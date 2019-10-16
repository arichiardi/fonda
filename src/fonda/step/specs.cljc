(ns fonda.step.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::on-start (s/nilable fn?))
(s/def ::on-success (s/nilable fn?))
(s/def ::on-error (s/nilable fn?))
(s/def ::on-complete (s/nilable fn?))
(s/def ::is-anomaly-error? (s/nilable fn?))

;; Tap step
(s/def ::tap (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::tap-step
  (s/keys :req-un [::tap]
          :opt-un [::on-start ::on-success ::on-error ::on-complete]))

;; Processor step
(s/def ::path (s/nilable vector?))
(s/def ::fn (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::processor-step
  (s/keys :req-un [::fn]
          :opt-un [::path
                   ::on-start
                   ::on-success
                   ::on-error
                   ::on-complete
                   ::is-anomaly-error?]))

;; Injector step
(s/def ::inject (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::injector-step
  (s/keys :req-un [::inject]))
