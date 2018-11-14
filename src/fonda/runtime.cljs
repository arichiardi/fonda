(ns fonda.runtime
  (:require [cljs.spec.alpha :as s]
            [fonda.step :as st]))

(s/def ::anomaly? fn?)
(s/def ::exception-tap (s/nilable fn?))
(s/def ::anomaly-tap (s/nilable fn?))
(s/def ::ctx (s/nilable map?))
(s/def ::error (s/nilable #(instance? js/Error %)))
(s/def ::anomaly (s/nilable any?))
(s/def ::on-complete fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly fn?)
(s/def ::queue (s/coll-of ::st/step))
(s/def ::step-log any?)
(s/def ::log-step-fn fn?)

(s/def ::runtime-context
  (s/keys :req-un [::anomaly?
                   ::exception-tap
                   ::anomaly-tap
                   ::ctx
                   ::on-complete
                   ::on-anomaly
                   ::on-exception
                   ::queue
                   ::step-log
                   ::log-step-fn
                   ::error
                   ::anomaly]))

(defrecord RuntimeContext
  [

   ;; A function that gets a map and determines if it is an anomaly
   anomaly?

   ;; A function gets called with the runtime-context when there is an exception
   exception-tap

   ;; A function that gets called with te runtime-context when a step returns an anomaly
   anomaly-tap

   ;; The context data that gets passed to the step functions
   ctx

   ;; An exception thrown by a step
   error

   ;; An anomaly returned by a step
   anomaly

   ;; Callback function that gets called with the context after all the steps succeeded
   on-complete

   ;; Callback function that gets called with an exception that a step triggered
   on-exception

   ;; Callback function taht gets called with an anomaly that a step returned
   on-anomaly

   ;; The steps that haven't been already processed
   queue

   ;; A log, each step can add information here
   step-log

   ;; A function that defines how each step adds information to the log
   log-step-fn])

