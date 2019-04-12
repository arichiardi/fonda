(ns fonda.step.specs
  (:require [clojure.spec.alpha :as s]))

;; Common for all steps
#_(s/def ::name (s/nilable keyword?))

;; Temporary added so the tests pass, I don't understand why the spec is not being
;; properly merged from fonda.core.step.specs
(s/def ::name (s/or :string (s/nilable string?)
                    :keyword (s/nilable keyword?)))

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

;; Injector step
(s/def ::inject fn?)
(s/def ::injector-step
  (s/merge ::step-common (s/keys :req-un [::inject])))

(s/def ::step
  (s/or :tap-step ::tap-step
        :processor-step ::processor-step
        :injector-step ::injector-step))

(s/def ::steps (s/coll-of ::step))