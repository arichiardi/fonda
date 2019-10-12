(ns fonda.step.specs
  (:require [clojure.spec.alpha :as s]))

;; Tap step
(s/def ::tap (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::tap-step
  (s/keys :req-un [::tap]))

;; Processor step
(s/def ::path vector?)
(s/def ::fn (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::processor-step
  (s/keys :req-un [::fn ::path]))

;; Injector step
(s/def ::inject (s/or :function fn? :qualified-keyword qualified-keyword?))
(s/def ::injector-step
  (s/keys :req-un [::inject]))
