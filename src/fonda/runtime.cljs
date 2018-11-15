(ns fonda.runtime
  (:require [cljs.spec.alpha :as s]
            [fonda.step :as st]
            [fonda.async :as a]))

(s/def ::anomaly? fn?)
(s/def ::log-exception (s/nilable fn?))
(s/def ::log-anomaly (s/nilable fn?))
(s/def ::log-success (s/nilable fn?))
(s/def ::ctx map?)
(s/def ::exception (s/nilable #(instance? js/Error %)))
(s/def ::anomaly (s/nilable any?))
(s/def ::on-success fn?)
(s/def ::on-exception fn?)
(s/def ::on-anomaly fn?)
(s/def ::queue (s/coll-of ::st/step))
(s/def ::step-log any?)
(s/def ::log-step-fn fn?)

(s/def ::runtime-context-async a/async?)
(s/def ::runtime-context-map
  (s/keys :req-un [::anomaly?
                   ::log-exception
                   ::log-anomaly
                   ::log-success
                   ::ctx
                   ::on-success
                   ::on-anomaly
                   ::on-exception
                   ::queue
                   ::step-log
                   ::log-step-fn
                   ::exception
                   ::anomaly]))

(s/def ::runtime-context (s/or :async ::runtime-context-async
                               :sync ::runtime-context-map))
(defrecord RuntimeContext
  [

   ;; A function that gets a map and determines if it is an anomaly
   anomaly?

   ;; A function gets called with the runtime-context when there is an exception
   log-exception

   ;; A function that gets called with the runtime-context when a step returns an anomaly
   log-anomaly

   ;; A function that gets called with the runtime-context after all steps succeeded
   log-success

   ;; The context data that gets passed to the step functions
   ctx

   ;; An exception thrown by a step
   exception

   ;; An anomaly returned by a step
   anomaly

   ;; Callback function that gets called with the context after all the steps succeeded
   on-success

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

