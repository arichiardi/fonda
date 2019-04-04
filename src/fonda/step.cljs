(ns fonda.step
  (:require [cljs.spec.alpha :as s]
            [fonda.meta :as meta]))

;; Common for all steps
(s/def ::name string?)
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

(s/def ::step
  (s/or :tap-step ::tap-step
        :processor-step ::processor-step))

(defrecord Tap
  [;; A function that gets the context but doesn't augment it
   tap

   ;; The name for the step
   name])

(defrecord Processor
  [;; A function that gets the context, the result is attached to the context on the given path
   processor

   ;; Name for the step
   name

   ;; Path were to attach the processor result on the context
   path])

(defn resolve-function
  [fn-or-keyword]
  (if (qualified-keyword? fn-or-keyword)
    (meta/kw->fn fn-or-keyword)
    fn-or-keyword))

(defn step->record
  [{:keys [tap processor] :as step}]
  (cond
    tap (map->Tap (update step :tap resolve-function))
    processor (map->Processor (update step :processor resolve-function))))

(def ^{:doc "Step transducer."}
  xf
  (map step->record))
