(ns fonda.step
  (:require [cljs.spec.alpha :as s]))


;; Common for all steps
(s/def ::name (s/or :k keyword? :s string?))
(s/def ::step-common
  (s/keys :req-un [::name]))

;; Tap step
(s/def ::tap fn?)
(s/def ::tap-step
  (s/merge
    ::step-common
    (s/keys :req-un [::tap])))

;; Processor step
(s/def ::path vector?)
(s/def ::processor fn?)
(s/def ::processor-step
  (s/merge
    ::step-common
    (s/keys :req-un [::processor ::path])))

(s/def ::step
  (s/or :tap-step ::tap-step
        :processor-step ::processor-step))

(defrecord Tap
  [
   ;; A function that gets the context but doesn't augment it
   tap

   ;; The name for the step
   name])

(defrecord Processor
  [
   ;; A function that gets the context, the result is attached to the context on the given path
   processor

   ;; Name for the step
   name

   ;; Path were to attach the processor result on the context
   path])

(defn steps->queue [steps]
  (->> steps
       (mapv (fn [{:as m :keys [tap processor]}]
               (cond
                 tap (map->Tap m)
                 processor (map->Processor m)
                 :else (throw (ex-info "The step doesn't have tap neither resolve" m)))))
       (into #queue [])))