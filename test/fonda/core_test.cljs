(ns fonda.core-execute-test
  (:require [clojure.test :as test :refer-macros [deftest is testing async]]
            [fonda.core :as core]))

(defn unexpected [name stage]
  (fn [ctx]
    (throw (ex-info "unexpected invocation"
                    {:name name
                     :stage stage
                     :ctx ctx}))))

(defn make-test-step [name]
  {:name name
   :enter (unexpected name :enter)
   :leave (unexpected name :leave)
   :error (unexpected name :error)})

                                        ; Test stack with three steps and a handler:

(def test-conf {:steps [(make-test-step :a)
                        (make-test-step :b)
                        (make-test-step :c)
                        (unexpected :handler nil)]})

(defn fail! [& _]
  (throw (ex-info "Should never be called" {})))

(def error (ex-info "oh no" {::error-marker true}))

(defn always-throw [ctx]
  (throw error))

(defn error-handler-step [response]
  (fn [ctx]
    (assert (not (some? (:response ctx))))
    (assert (-> ctx :error ex-data (= {::error-marker true})))
    (-> ctx
        (dissoc :error)
        (assoc :response response))))

;;
;; Tests:
;;

(deftest execute-happy-path-test
  (async done
    (-> test-chain
        (assoc-in [a-index :enter] identity)
        (assoc-in [b-index :enter] identity)
        (assoc-in [c-index :enter] identity)
        (assoc-in [h-index] inc)
        (assoc-in [c-index :leave] identity)
        (assoc-in [b-index :leave] identity)
        (assoc-in [a-index :leave] identity)
        (sc/execute 41
                    (fn [input]
                      (is (= 42 input) "enable all enter and leave stages, use `inc` as handler")
                      (done))
                    fail!))))
