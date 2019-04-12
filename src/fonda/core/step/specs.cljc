(ns fonda.core.step.specs
  (:require [clojure.spec.alpha :as s]
            [fonda.step.specs :as step]))

;; step spec is redefined for fonda.core because the step name on core can be either a string or a keyword,
;; and the step name in fonda.execute can only be a keyword
(s/def ::name (s/or :string (s/nilable string?)
                    :keyword (s/nilable keyword?)))

(s/def ::step (s/merge ::step/step (s/keys :opt-un [::name])))

(s/def ::steps (s/coll-of ::step))
