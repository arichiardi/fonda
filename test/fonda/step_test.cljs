(ns fonda.step-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [orchestra-cljs.spec.test :as orchestra]
            [fonda.step :as step]))

(orchestra/instrument)

(deftest empty-steps-test
  (is (empty? (step/steps->queue []))))

(deftest steps-with-no-taps-test
  (let [steps [{:processor      identity
                :name           "test1"
                :path           [:test1]}

               {:processor      identity
                :name           "test2"
                :path           [:test2]}]]
    (is (= steps (->> (step/steps->queue steps) (mapv #(into {} %)))))))
