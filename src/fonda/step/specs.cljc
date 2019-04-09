(ns fonda.step.specs
  (:require [clojure.spec.alpha :as s]))

;; Common for all steps
(s/def ::name (s/nilable string?))
(s/def ::step-common
  (s/keys :opt-un [::name]))

;; Tap step
(s/def ::tap (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::tap-step
  (s/merge ::step-common (s/keys :req-un [::tap])))

;; Processor step
(s/def ::path vector?)

(s/def ::processor (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::processor-step
  (s/merge ::step-common (s/keys :req-un [::processor ::path])))
