(ns fonda.core-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [fonda.core :as fonda]
            [fonda.core.specs]
            [fonda.execute.specs]
            [orchestra-cljs.spec.test :as orchestra]))

(orchestra/instrument)

(defn success-cb-throw [res]
  (throw (js/Error (str "unexpected success callback called with res:" res))))

(defn exception-cb-throw [err]
  (throw err))

(defn anomaly-cb-throw [anomaly]
  (throw (js/Error (str "unexpected anomaly callback called with anomaly:" anomaly))))

(defn anomaly
  ([category]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category}})
  ([category message]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category
                                                        :message  message}}))

(deftest execute-empty-chain-test-1
  (testing "Passing empty configuration with empty steps should call on-success with a nil value."
    (async done
      (fonda/execute {}
                     []
                     exception-cb-throw
                     (fn [res]
                       (is (= {} res))
                       (done))))))

(deftest execute-empty-chain-test-2
  (testing "Passing a context on the configuration with empty steps should call on-success with that context."
    (let [initial {:foo :bar}]
      (async done
        (fonda/execute {:initial-ctx initial}
                       []
                       exception-cb-throw
                       (fn [res]
                         (is (= initial res))
                         (done)))))))

(deftest one-successful-sync-processor-test
  (testing "Passing one synchronous processor should call on-success with the context augmented with the processor result on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :processor (constantly processor-res)}]
        (fonda/execute {}
                       [processor]
                       exception-cb-throw
                       (fn [res]
                         (is (= processor-res (get-in res processor-path)))
                         (done)))))))

(deftest one-successful-sync-tap-doesnt-augment-context-test
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial {:foo :bar}
            tap {:name "tap1"
                 :tap  (constantly :whatever-value)}]
        (fonda/execute {:initial-ctx initial}
                       [tap]
                       exception-cb-throw
                       (fn [res]
                         (is (= initial res))
                         (done)))))))

(deftest one-successful-sync-tap-is-passed-the-context
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial {:foo :bar}
            tap {:tap (fn [ctx]
                        (is (= initial ctx))
                        (done))}]
        (fonda/execute {:initial-ctx initial}
                       [tap]
                       exception-cb-throw
                       (fn [_]))))))

(deftest one-successful-async-processor-test
  (testing "Passing one asynchronous processor should call on-success with the context augmented with the processor result on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :processor (constantly (js/Promise.resolve processor-res))}]
        (fonda/execute {}
                       [processor]
                       exception-cb-throw
                       (fn [res]
                         (is (= processor-res (get-in res processor-path)))
                         (done)))))))

(deftest one-unsuccessful-sync-processor-test
  (testing "Passing one synchronous unsuccessful processor should call on-anomaly with the anomaly after calling the anomaly-handler"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "step1"
                       :processor (constantly processor-res)}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {"step1" #(reset! anomaly-handler-arg (:anomaly %))}}
                       [processor]
                       exception-cb-throw
                       (fn [_])
                       (fn [anomaly]
                         (is (= @anomaly-handler-arg processor-res))
                         (is (= processor-res anomaly)) (done)))))))

(deftest one-unsuccessful-sync-tap-test
  (testing "Passing one synchronous unsuccessful tap should call on-anomaly with the anomaly after calling the anomaly
  handler, and passing step keyword"
    (async done
      (let [tap-res (anomaly :cognitect.anomalies/incorrect)
            tap {:path [:processor-path]
                 :name :step1
                 :tap  (constantly tap-res)}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {"step1" #(reset! anomaly-handler-arg (:anomaly %))}}
                       [tap]
                       exception-cb-throw
                       success-cb-throw
                       (fn [anomaly]
                         (is (= @anomaly-handler-arg tap-res))
                         (is (= tap-res anomaly))
                         (done)))))))

(deftest one-unsuccessful-async-processor-test
  (testing "Passing one asynchronous unsuccessful processor should call on-anomaly with the anomaly after calling the
  anomaly handler, using keywords for the handler keys"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "step1"
                       :processor (constantly (js/Promise.resolve processor-res))}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [processor]
                       exception-cb-throw
                       success-cb-throw
                       (fn [anomaly]
                         (is (= @anomaly-handler-arg processor-res))
                         (is (= processor-res anomaly))
                         (done)))))))

(deftest one-exceptional-sync-processor-test
  (testing "Passing one synchronous exceptional processor should call on-exception with the exception after calling the
   exception handler"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "step1"
                       :processor (fn [_] (throw processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step1 #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [err]
                         (is (= @exception-handler-arg processor-res))
                         (is (= processor-res err))
                         (done))
                       success-cb-throw)))))

(deftest one-exceptional-sync-tap-test
  (testing "Passing one synchronous exceptional tap should call on-exception with the exception after calling the
  exception handler, using keywords for steps and handlers keys"
    (async done
      (let [tap-res (js/Error "Bad exception")
            tap {:tap  (fn [_] (throw tap-res))
                 :name :step1}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step1 #(reset! exception-handler-arg (:exception %))}}
                       [tap]
                       (fn [err]
                         (is (= @exception-handler-arg tap-res))
                         (is (= tap-res err))
                         (done))
                       success-cb-throw)))))

(deftest one-exceptional-async-processor-test
  (testing "Passing one asynchronous exceptional processor should call on-exception with the anomaly after calling the
  exception handler"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "step1"
                       :processor (constantly (js/Promise.reject processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {"step1" #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [err]
                         (is (= @exception-handler-arg processor-res))
                         (is (= processor-res err))
                         (done))
                       success-cb-throw)))))

(deftest one-exceptional-async-processor-test-name-not-defined
  (testing "Passing one asynchronous exceptional processor should call on-exception with the anomaly and it should not
  call any exception handler"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :processor (constantly (js/Promise.reject processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {"step1" #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [err]
                         (is (nil? @exception-handler-arg))
                         (is (= processor-res err))
                         (done))
                       success-cb-throw)))))

(deftest multiple-successful-synchronous-steps-test
  (testing "Passing multiple successful synchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path [:step1] :processor (constantly step1-val)}
                        {:path [:step2] :processor (fn [{:keys [step1]}] (step2-fn step1))}]
                       exception-cb-throw
                       (fn [res]
                         (is (= res {:step1 step1-val
                                     :step2 (step2-fn step1-val)}))
                         (done)))))))

(deftest multiple-successful-asynchronous-steps-augmented-context-on-success-test
  (testing "Passing multiple successful asynchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path      [:step1]
                         :processor (constantly (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :processor (fn [{:keys [step1]}]
                                      (js/Promise.resolve (step2-fn step1)))}]
                       exception-cb-throw
                       (fn [res]
                         (is (= res {:step1 step1-val
                                     :step2 (step2-fn step1-val)}))
                         (done)))))))


(deftest multiple-successful-asynchronous-and-synchronous-steps-test
  (testing "Passing multiple successful asynchronous and synchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc
            step3-fn str]
        (fonda/execute {}
                       [{:path      [:step1]
                         :processor (constantly (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :processor (fn [{:keys [step1]}]
                                      (step2-fn step1))}
                        {:path      [:step3]
                         :processor (fn [{:keys [step2]}]
                                      (step3-fn step2))}]
                       exception-cb-throw
                       (fn [res]
                         (is (= res {:step1 step1-val
                                     :step2 (step2-fn step1-val)
                                     :step3 (-> step1-val (step2-fn) (step3-fn))}))
                         (done)))))))


(deftest multiple-steps-one-unsuccessful-calls-on-anomaly-test
  (testing "When :anomaly is true and an anomaly occurs it should call on-anomaly after calling the anomaly handler"
    (async done
      (let [unsuccessful-anomaly (anomaly :cognitect.anomalies/incorrect)
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {"step2" #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :processor (constantly (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (constantly unsuccessful-anomaly)}

                        {:path      [:step3]
                         :processor (constantly 3)}]
                       exception-cb-throw
                       success-cb-throw
                       (fn [anomaly]
                         (is (= @anomaly-handler-arg anomaly))
                         (is (= unsuccessful-anomaly anomaly) "it should call the on-anomaly callback with the anomaly data")
                         (done)))))))


(deftest multiple-steps-on-anomaly-do-not-short-circuit-test
  (testing "When :anomaly is false and an anomaly occurs it should not call on-anomaly callback neither the anomaly
  handlers"
    (async done
      (let [successful-anomaly (anomaly :cognitect.anomalies/incorrect)
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         false
                        :anomaly-handlers {"step2" #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :processor (constantly (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (constantly successful-anomaly)}

                        {:path      [:step3]
                         :processor (constantly 3)}]
                       exception-cb-throw
                       (fn [ctx]
                         (is (nil? @anomaly-handler-arg))
                         (is (= {:step1 1
                                 :step2 successful-anomaly
                                 :step3 3}
                                ctx)
                             "it should call the on-success callback with the anomaly in the context")
                         (done))
                       anomaly-cb-throw)))))


(deftest multiple-steps-one-unsuccessful-short-circuits-test
  (testing "When :anomaly is true and an anomaly occurs"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)
            step1-counter (atom 0)
            step3-counter (atom 0)
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {"step2" #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] unsuccessful-res)}

                        {:path      [:step3]
                         :processor (fn [_] (swap! step3-counter inc))}]
                       exception-cb-throw
                       success-cb-throw
                       (fn [anomaly]
                         (is (= @anomaly-handler-arg anomaly))
                         (is (and (= 1 @step1-counter)
                                  (= 0 @step3-counter))
                             "it should not call the previous but not the subsequent steps")
                         (done)))))))

(deftest multiple-steps-one-exceptional-calls-on-exception-test
  (testing "When an exception occurs"
    (async done
      (let [exception (js/Error "Bad exception")
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {"step2" #(reset! exception-handler-arg (:exception %))}}
                       [{:path      [:step1]
                         :processor (fn [_]
                                      (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] (throw exception))}

                        {:path      [:step3]
                         :processor (fn [_] 1)}]
                       (fn [err]
                         (is (= @exception-handler-arg exception))
                         (is (= exception err) "it should call on-exception passing the js/Error")
                         (done))
                       success-cb-throw)))))

(deftest multiple-steps-one-exceptional-short-circuits-test
  (testing "When an exception occurs"
    (async done
      (let [exception (js/Error "Bad exception")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :processor (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :processor (fn [_] (swap! step3-counter inc))}]
                       (fn [err]
                         (is (and (= 1 @step1-counter)
                                  (= 0 @step3-counter))
                             "it should not call the previous but not the subsequent steps")
                         (done))
                       success-cb-throw)))))

(deftest multiple-steps-one-exceptional-tap-short-circuits-test
  (testing "When an exception in a tap occurs"
    (async done
      (let [exception (js/Error "Bad exception")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:tap (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :processor (fn [_] (swap! step3-counter inc))}]
                       (fn [err]
                         (is (and (= 1 @step1-counter)
                                  (= 0 @step3-counter))
                             "it should not call the previous but not the subsequent steps")
                         (done))
                       success-cb-throw)))))

(deftest injected-step-should-run-after-injector
  (testing "Injecting one step should add the step after the injector"
    (async done
      (fonda/execute {:initial-ctx {:steps []}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :processor (fn [{:keys [steps]}]
                                    (conj steps :step1))}
                      {:inject (fn [_]
                                 {:path      [:steps]
                                  :name      "injected-step"
                                  :processor (fn [{:keys [steps]}]
                                               (conj steps :injected-step))})
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :processor (fn [{:keys [steps]}]
                                    (conj steps :step2))}]
                     exception-cb-throw
                     (fn [res] (is (= res {:steps [:step1 :injected-step :step2]})) (done))
                     anomaly-cb-throw))))

(deftest injected-steps-should-run-after-injector
  (testing "Injecting multiple steps should add the steps after the injector"
    (async done
      (fonda/execute {:initial-ctx {:steps []}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :processor (fn [{:keys [steps]}]
                                    (conj steps :step1))}
                      {:inject (fn [_]
                                 [{:path      [:steps]
                                   :name      "injected-step1"
                                   :processor (fn [{:keys [steps]}]
                                                (conj steps :injected-step1))}
                                  {:path      [:steps]
                                   :name      "injected-step2"
                                   :processor (fn [{:keys [steps]}]
                                                (conj steps :injected-step2))}])
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :processor (fn [{:keys [steps]}]
                                    (conj steps :step2))}]
                     exception-cb-throw
                     (fn [res] (is (= res {:steps [:step1 :injected-step1 :injected-step2 :step2]})) (done))
                     anomaly-cb-throw))))

(deftest lonely-injector-with-one-step
  (testing "Only one injector on the steps"
    (async done
      (fonda/execute {:initial-ctx {:steps []}}
                     [{:inject (fn [_]
                                 {:path      [:steps]
                                  :name      "injected-step"
                                  :processor (fn [{:keys [steps]}]
                                               (conj steps :injected-step))})
                       :name   "injector1"}]
                     exception-cb-throw
                     (fn [res] (is (= res {:steps [:injected-step]})) (done))
                     anomaly-cb-throw))))

(deftest lonely-injector-with-multiple-steps
  (testing "Only one injector on the steps"
    (async done
      (fonda/execute {:initial-ctx {:steps []}}
                     [{:inject (fn [_]
                                 [{:path      [:steps]
                                   :name      "injected-step1"
                                   :processor (fn [{:keys [steps]}]
                                                (conj steps :injected-step1))}
                                  {:path      [:steps]
                                   :name      "injected-step2"
                                   :processor (fn [{:keys [steps]}]
                                                (conj steps :injected-step2))}])
                       :name   "injector1"}]
                     exception-cb-throw
                     (fn [res] (is (= res {:steps [:injected-step1 :injected-step2]})) (done))
                     anomaly-cb-throw))))