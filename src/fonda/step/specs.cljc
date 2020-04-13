(ns fonda.step.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::on-start (s/nilable any?))
(s/def ::on-success (s/nilable any?))
(s/def ::on-error (s/nilable any?))
(s/def ::on-complete (s/nilable any?))
(s/def ::is-anomaly-error? (s/nilable fn?))

;; Tap step
(s/def ::tap (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::tap-step
  (s/keys :req-un [::tap]
          :opt-un [::on-start
                   ::on-success
                   ::on-error
                   ::on-complete]))

;; Processor step
(s/def ::path (s/nilable vector?))
(s/def ::fn (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::processor (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::processor-common
  (s/keys :opt-un [::path
                   ::on-start
                   ::on-success
                   ::on-error
                   ::on-complete
                   ::is-anomaly-error?]))
(s/def ::processor-step
  (s/or :processor-legacy (s/merge ::processor-common (s/keys :req-un [::processor]))
        :processor-fn (s/merge ::processor-common (s/keys :req-un [::fn]))))

;; Injector step
(s/def ::inject (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::injector-step
  (s/keys :req-un [::inject]))
