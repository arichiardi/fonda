(ns fonda.core-execute-test
  (:require [clojure.test :as test :refer-macros [deftest is testing async]]
            [fonda.core :as core]))

#_(execute {

            ;; [opt] By default, clojure anomaly
            :anomaly? (fn [x] (:my-weird-error x))

            ;; [opt]
            :exception-tap (fn [{:keys [ctx exception step-log]}])

            :anomaly-tap (fn [{:keys [ctx anomaly step-log]}])

            ;; Optional
            :ctx-seed {}

            ;; TODO: PLUGGABLE DEBUGGING
            :debug {}
          }

         [
          ;; Blocking tap, it short-circuits if it throws an exception
          {:tap (fn [ctx])}

          {:path     [:something]
           :name     "step-name"

           ;; Resolver can return data, an anomaly, or throw an exception
           :resolver (fn [])

           :retry    {:path    [:path-to-retry-data]
                      :name    "step-name"
                      ;; Resolver can return the updated counter data, an anomaly, or throw an exception
                      ;; If it returns data synchronously, it immediately repeats
                      ;; If it returns data in a promise, the time until the promise gets resolved is the delay
                      :delayer (fn [{:keys [ctx anomaly]}]
                                 {:updated "retry-data"})}}
          ]

         ;; success cb
         (fn [result])

         ;; anomaly cb
         (fn [anomaly])

         ;; exception cb
         (fn [exception])

         )
