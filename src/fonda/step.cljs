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

;; Resolver step
(s/def ::path vector?)
(s/def ::resolver fn?)
(s/def ::resolver-step
  (s/merge
    ::step-common
    (s/keys :req-un [::resolver ::path])))

(s/def ::step
  (s/or :tap-step ::tap-step
        :resolver-step ::resolver-step))

(defrecord TapStep
  [
   ;; A function that gets the context but doesn't augment it
   tap

   ;; The name for the step
   name])

(defrecord ResolverStep
  [
   ;; A function that gets the context, the result is attached to the context on the given path
   resolver

   ;; Name for the step
   name

   ;; Path were to attach the resolver result on the context
   path])

(defn steps->queue [steps]
  (->> steps
       (mapv (fn [{:as m :keys [tap resolver]}]
               (cond
                 tap (map->TapStep m)
                 resolver (map->ResolverStep m)
                 :else (throw (ex-info "The step doesn't have tap neither resolve" m)))))
       (into #queue [])))