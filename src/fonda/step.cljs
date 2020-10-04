(ns fonda.step
  (:require [fonda.meta :as meta]))


(defrecord Tap
  [;; A function that gets the context but doesn't augment it
   tap

   ;; The name for the step
   name

   on-start

   on-success

   on-error

   on-complete
   ])

(defrecord Processor
  [;; A function that gets the context, the result is attached to the context on the given path
   fn

   ;; Name for the step
   name

   ;; Path were to attach the processor result on the context
   path

   on-start

   on-success

   on-error

   on-complete

   is-anomaly-error?
   ])

(defrecord Injector
  [;; Function that returns step(s) to be injected  right after this step on the queue
   inject

   ;; Name for the step
   name])

(defn resolve-function
  [fn-or-keyword]
  (if (qualified-keyword? fn-or-keyword)
    (meta/kw->fn fn-or-keyword)
    fn-or-keyword))

(defn step->record
  [{:keys [tap inject fn processor] :as step}]
  (let [step (cond
               tap (map->Tap (update step :tap resolve-function))
               (or processor fn) (map->Processor (update step :fn resolve-function))
               inject (map->Injector (update step :inject resolve-function)))]
    (-> step
        (update :name keyword)
        (assoc :is-anomaly-error? (constantly true)))))

(def ^{:doc "Step transducer."} xf (map step->record))
