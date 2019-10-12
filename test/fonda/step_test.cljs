(ns fonda.step-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fonda.step :as step]
            [fonda.step.specs]
            [orchestra-cljs.spec.test :as orchestra]))

(orchestra/instrument)

(deftest processor-step-test
  (let [step (step/step->record {:fn :cljs.core/inc
                                 :path [:test]})]
    (is (record? step) "the step should be a record")
    (is (fn? (:fn step)) "the :fn key should become a function")))

(deftest tap-step-test
  (let [step (step/step->record {:tap :cljs.core/println
                                 :path [:test]})]
    (is (record? step) "the step should be a record")
    (is (fn? (:tap step)) "the :tap key should become a function")))

(deftest injector-step-test
  (let [step (step/step->record {:inject :cljs.core/println})]
    (is (record? step) "the injector should be a record")
    (is (fn? (:inject step)) "the :inject key should become a function")))
