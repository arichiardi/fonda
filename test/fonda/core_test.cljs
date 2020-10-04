(ns fonda.core-test
  (:require [cljs.test :refer-macros [deftest is testing async use-fixtures]]
            [fonda.core :as fonda]
            [fonda.core.specs]
            [orchestra-cljs.spec.test :as orchestra]))

(orchestra/instrument)

(def events (atom []))
(defn cb-with-result [event]
  (fn [ctx result]
    (swap! events conj [event result ctx])))

(defn cb-no-result [event]
  (fn [ctx]
    (swap! events conj [event ctx])))


(use-fixtures :each {:before (fn [] (reset! events []))})

(defn success-cb-throw [ctx res]
  (throw (js/Error (str "unexpected success callback called with res:" res " and ctx:" ctx))))

(defn exception-cb-throw [ctx err]
  (throw err))

(defn anomaly-cb-throw [ctx anomaly]
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
                     (fn [ctx res]
                       (is (= @events []))
                       (is (= {} ctx))
                       (done))))))

(deftest execute-empty-chain-test-2
  (testing "Passing a context on the configuration with empty steps should call on-success with that context."
    (let [initial {:foo :bar}]
      (async done
        (fonda/execute {:ctx initial}
                       []
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= initial ctx))
                         (done)))))))

(deftest one-successful-sync-processor-test
  (testing "Passing one synchronous processor should call on-success with the context augmented with the processor result on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:on-success (cb-with-result ::auth-success)
                       :path processor-path
                       :fn (constantly processor-res)}]
        (fonda/execute {}
                       [processor]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= processor-res (get-in ctx processor-path) res))
                         (is (= @events [[::auth-success processor-res {:processor-path processor-res}]]))
                         (done)))))))

(deftest one-successful-sync-processor-with-no-path-doesnt-contribute-to-ctx-test
  (testing "Passing one synchronous processor with no path should call on-success with the context untouched"
    (async done
      (let [initial-ctx {:initial "value"}
            processor-res 42
            processor {:fn (constantly processor-res)
                       :on-complete (cb-with-result ::complete)}]
        (fonda/execute {:ctx initial-ctx}
                       [processor]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= res processor-res))
                         (is (= ctx initial-ctx))
                         (is (= @events [[::complete processor-res initial-ctx]]))
                         (done)))))))

(deftest one-successful-sync-mocked-processor-test
  (testing "The mocked function should replace the function on the step"
    (async done
      (let [processor-res 42
            mocked-processor-res 43
            processor-path [:processor-path]
            processor {:path processor-path
                       :fn (constantly processor-res)
                       :name ::step-1
                       :on-start (cb-no-result ::start)}]
        (fonda/execute {:mock-fns {::step-1 (constantly mocked-processor-res)}}
                       [processor]
                       exception-cb-throw
                       (fn [ctx res]
                         ;; The on-start callback was called before the step, with the initial ctx
                         (is (= @events [[::start {}]]))
                         (is (= mocked-processor-res (get-in ctx processor-path)))
                         (done)))))))

(deftest one-successful-sync-tap-doesnt-augment-context-test
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial {:foo :bar}
            tap {:name "tap1"
                 :tap  (constantly :whatever-value)}]
        (fonda/execute {:ctx initial}
                       [tap]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= initial ctx))
                         (done)))))))

(deftest one-successful-sync-tap-is-passed-the-context
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial {:foo :bar}
            tap {:tap (fn [ctx]
                        (is (= initial ctx))
                        (done))}]
        (fonda/execute {:ctx initial}
                       [tap]
                       exception-cb-throw
                       (fn [_]))))))

(deftest one-successful-mocked-sync-tap-is-passed-the-context
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial {:foo :bar}
            tap {:tap (fn [_])
                 :name ::tap-step}]
        (fonda/execute {:ctx initial
                        :mock-fns {::tap-step (fn [ctx]
                                                (is (= initial ctx))
                                                (done))}}
                       [tap]
                       exception-cb-throw
                       (fn [_]))))))

(deftest one-successful-async-processor-test
  (testing "Passing one asynchronous processor should call on-success with the context augmented with the processor result on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :fn (constantly (js/Promise.resolve processor-res))}]
        (fonda/execute {}
                       [processor]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= processor-res (get-in ctx processor-path)))
                         (done)))))))

(deftest one-successful-async-mocked-processor-test
  (testing "The mocked function should replace the function on the step"
    (async done
      (let [mocked-processor-res 43
            processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :fn (constantly (js/Promise.resolve processor-res))
                       :name ::step-1}]
        (fonda/execute {:mock-fns {::step-1 (constantly (js/Promise.resolve mocked-processor-res))}}
                       [processor]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= mocked-processor-res (get-in ctx processor-path)))
                         (done)))))))

(deftest one-unsuccessful-sync-processor-test
  (testing "Passing one synchronous unsuccessful processor should call on-anomaly with the anomaly after calling the anomaly-handler"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "step1"
                       :fn (constantly processor-res)
                       :on-error (cb-with-result ::error)}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [processor]
                       exception-cb-throw
                       (fn [_])
                       (fn [ctx anomaly]
                         (is (= @anomaly-handler-arg processor-res))
                         (is (= processor-res anomaly))
                         (is (= @events [[::error anomaly {:processor-path anomaly}]]))
                         (done)))))))

(deftest one-unsuccessful-sync-processor-with-no-path-test
  (testing "Passing one synchronous unsuccessful processor should call on-anomaly with the anomaly after calling the anomaly-handler"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:name      "step1"
                       :fn (constantly processor-res)}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [processor]
                       exception-cb-throw
                       (fn [_])
                       (fn [ctx anomaly]
                         (is (= @anomaly-handler-arg processor-res))
                         (is (= processor-res anomaly)) (done)))))))

(deftest one-unsuccessful-mocked-sync-processor-test
  (testing "Passing one synchronous unsuccessful mocked processor should call on-anomaly with the anomaly after calling the anomaly-handler"
    (async done
      (let [mocked-processor-res (anomaly :cognitect.anomalies/incorrect)
            processor-res (anomaly ::another-anomaly)
            processor {:path      [:processor-path]
                       :name      "step1"
                       :fn (constantly processor-res)}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}
                        :mock-fns {:step1 (constantly mocked-processor-res)}}
                       [processor]
                       exception-cb-throw
                       (fn [_])
                       (fn [ctx anomaly]
                         (is (= @anomaly-handler-arg mocked-processor-res))
                         (is (= mocked-processor-res anomaly)) (done)))))))

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
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [tap]
                       exception-cb-throw
                       success-cb-throw
                       (fn [ctx anomaly]
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
                       :fn (constantly (js/Promise.resolve processor-res))}
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step1 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [processor]
                       exception-cb-throw
                       success-cb-throw
                       (fn [ctx anomaly]
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
                       :fn (fn [_] (throw processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step1 #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [ctx err]
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
                       (fn [ctx err]
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
                       :fn (constantly (js/Promise.reject processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step1 #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [ctx err]
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
                       :fn (constantly (js/Promise.reject processor-res))}
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step1 #(reset! exception-handler-arg (:exception %))}}
                       [processor]
                       (fn [ctx err]
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
                       [{:path [:step1] :fn (constantly step1-val)}
                        {:path [:step2] :fn (fn [{:keys [step1]} _] (step2-fn step1))}]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= ctx {:step1 step1-val
                                     :step2 (step2-fn step1-val)}))
                         (done)))))))

(deftest multiple-successful-synchronous-steps-success-receives-last-step-result-test
  (testing "Passing multiple successful synchronous steps should call the on-success callback with the result of the last step"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path [:step1] :fn (constantly step1-val)}
                        {:path [:step2] :fn (fn [{:keys [step1]}] (step2-fn step1))}]
                       exception-cb-throw
                       (fn [_ step2]
                         (is (= step2 (step2-fn step1-val)))
                         (done)))))))

(deftest taps-dont-contribute-result-to-next-step
  (testing "Passing multiple successful synchronous steps should call the on-success callback with the result of the last step"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path [:step1] :fn (constantly step1-val)}
                        {:tap (constantly "tap-res")}
                        {:path [:step2] :fn (fn [{:keys [step1]}] (step2-fn step1))}]
                       exception-cb-throw
                       (fn [_ step2]
                         (is (= step2 (step2-fn step1-val)))
                         (done)))))))

(deftest multiple-successful-asynchronous-steps-augmented-context-on-success-test
  (testing "Passing multiple successful asynchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path      [:step1]
                         :fn (constantly (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :fn (fn [{:keys [step1]} _]
                                      (js/Promise.resolve (step2-fn step1)))}]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= ctx {:step1 step1-val
                                     :step2 (step2-fn step1-val)}))
                         (done)))))))

(deftest multiple-successful-asynchronous-steps-using-prev-step-res-augmented-context-on-success-test
  (testing "Passing multiple successful asynchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path      [:step1]
                         :fn (constantly (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :fn (fn [{:keys [step1]}]
                               (js/Promise.resolve (step2-fn step1)))}]
                       exception-cb-throw
                       (fn [ctx res]
                         (is (= res (step2-fn step1-val)))
                         (is (= ctx {:step1 step1-val
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
                         :fn (constantly (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :fn (fn [{:keys [step1]}]
                                      (step2-fn step1))}
                        {:path      [:step3]
                         :fn (fn [{:keys [step2]}]
                                      (step3-fn step2))}]
                       exception-cb-throw
                       (fn [ctx step3]
                         (let [step3-res (-> step1-val (step2-fn) (step3-fn))]
                           (is (= step3 step3-res))
                           (is (= ctx {:step1 step1-val
                                       :step2 (step2-fn step1-val)
                                       :step3 step3-res})))
                         (done)))))))


(deftest multiple-steps-one-unsuccessful-calls-on-anomaly-test
  (testing "When :anomaly is true and an anomaly occurs it should call on-anomaly after calling the anomaly handler"
    (async done
      (let [unsuccessful-anomaly (anomaly :cognitect.anomalies/incorrect)
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step2 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :fn (constantly (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :fn (constantly unsuccessful-anomaly)}

                        {:path      [:step3]
                         :fn (constantly 3)}]
                       exception-cb-throw
                       success-cb-throw
                       (fn [ctx anomaly]
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
                        :anomaly-handlers {:step2 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :fn (constantly (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :fn (constantly successful-anomaly)}

                        {:path      [:step3]
                         :fn (constantly 3)}]
                       exception-cb-throw
                       (fn [ctx res]
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
                        :anomaly-handlers {:step2 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :fn (fn [_] (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :name      "step2"
                         :fn (fn [_] unsuccessful-res)}

                        {:path      [:step3]
                         :fn (fn [_] (swap! step3-counter inc))}]
                       exception-cb-throw
                       success-cb-throw
                       (fn [ctx anomaly]
                         (is (= @anomaly-handler-arg anomaly))
                         (is (and (= 1 @step1-counter)
                                  (= 0 @step3-counter))
                             "it should not call the previous but not the subsequent steps")
                         (done)))))))

(deftest multiple-steps-one-not-breaking-anomaly
  (testing "When :anomaly is true and an anomaly occurs, and the anomaly is not breaking, it shouldn't short-circuit"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)
            step1-counter (atom 0)
            step3-atom (atom nil)
            anomaly-handler-arg (atom nil)]
        (fonda/execute {:anomaly?         true
                        :anomaly-handlers {:step2 #(reset! anomaly-handler-arg (:anomaly %))}}
                       [{:path      [:step1]
                         :fn (fn [_]
                               (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :name      "step2"
                         :fn (fn [_] unsuccessful-res)
                         :is-anomaly-error? (fn [a] false)}

                        {:path [:step3]
                         :fn   (fn [{:keys [step2]}]
                                 (reset! step3-atom step2)
                                 "not anomaly result")}]

                       exception-cb-throw
                       (fn [ctx res]
                         (is (nil? @anomaly-handler-arg)
                             "The on-anomaly callback should not be called")
                         (is (= 1 @step1-counter)
                             "it should have called the previous step")
                         (is (= unsuccessful-res @step3-atom)
                             "it should also call the step after the not-error anomaly with the anomaly")
                         (done))
                       anomaly-cb-throw)))))

(deftest multiple-steps-one-exceptional-calls-on-exception-test
  (testing "When an exception occurs"
    (async done
      (let [exception (js/Error "Bad exception")
            exception-handler-arg (atom nil)]
        (fonda/execute {:exception-handlers {:step2 #(reset! exception-handler-arg (:exception %))}}
                       [{:path      [:step1]
                         :fn (fn [_] (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :fn (fn [_] (throw exception))}

                        {:path      [:step3]
                         :fn (fn [_] 1)}]
                       (fn [ctx err]
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
                         :fn (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :fn (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :fn (fn [_] (swap! step3-counter inc))}]
                       (fn [ctx err]
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
                         :fn (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:tap (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :fn (fn [_] (swap! step3-counter inc))}]
                       (fn [ctx err]
                         (is (and (= 1 @step1-counter)
                                  (= 0 @step3-counter))
                             "it should not call the previous but not the subsequent steps")
                         (done))
                       success-cb-throw)))))

(deftest injected-step-should-run-after-injector
  (testing "Injecting one step should add the step after the injector"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :fn (fn [{:keys [steps]}]
                                    (conj steps :step1))}
                      {:inject (fn [_]
                                 {:path      [:steps]
                                  :name      "injected-step"
                                  :fn (fn [{:keys [steps]} _]
                                               (conj steps :injected-step))})
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :fn (fn [{:keys [steps]} _]
                                    (conj steps :step2))}]
                     exception-cb-throw
                     (fn [ctx res] (is (= ctx {:steps [:step1 :injected-step :step2]})) (done))
                     anomaly-cb-throw))))

(deftest injector-should-receive-ctx
  (testing "Injecting one step should add the step after the injector"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :fn (fn [{:keys [steps]}]
                             (conj steps :step1))}
                      {:inject (fn [ctx]
                                 (is (= ctx {:steps [:step1]}))
                                 {:path [:steps]
                                  :name "injected-step"
                                  :fn   (fn [{:keys [steps]} _]
                                          (conj steps :injected-step))})
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :fn (fn [{:keys [steps]} _]
                             (conj steps :step2))}]
                     exception-cb-throw
                     (fn [ctx] (is (= ctx {:steps [:step1 :injected-step :step2]})) (done))
                     anomaly-cb-throw))))

(deftest injected-steps-should-run-after-injector
  (testing "Injecting multiple steps should add the steps after the injector"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :fn (fn [{:keys [steps]}]
                                    (conj steps :step1))}
                      {:inject (fn [_]
                                 [{:path      [:steps]
                                   :name      "injected-step1"
                                   :fn (fn [{:keys [steps]} _]
                                                (conj steps :injected-step1))}
                                  {:path      [:steps]
                                   :name      "injected-step2"
                                   :fn (fn [{:keys [steps]} _]
                                                (conj steps :injected-step2))}])
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :fn (fn [{:keys [steps]} _]
                                    (conj steps :step2))}]
                     exception-cb-throw
                     (fn [ctx res] (is (= ctx {:steps [:step1 :injected-step1 :injected-step2 :step2]})) (done))
                     anomaly-cb-throw))))

(deftest injected-mocked-steps-should-run-after-injector
  (testing "Injected steps are also mocked when injecting multiple steps"
    (async done
      (fonda/execute {:ctx {:steps []}
                      :mock-fns {:injected-step1 (fn [{:keys [steps]} _] (conj steps :mocked-injected-step1))
                                 :injected-step2 (fn [{:keys [steps]} _] (conj steps :mocked-injected-step2))}}
                     [{:path      [:steps]
                       :name      "processor1"
                       :fn (fn [{:keys [steps]}]
                             (conj steps :step1))}
                      {:inject (fn [_]
                                 [{:path      [:steps]
                                   :name      "injected-step1"
                                   :fn (fn [{:keys [steps]} _]
                                         (conj steps :injected-step1))}
                                  {:path      [:steps]
                                   :name      "injected-step2"
                                   :fn (fn [{:keys [steps]} _]
                                         (conj steps :injected-step2))}])
                       :name   "injector1"}
                      {:path      [:steps]
                       :name      "processor2"
                       :fn (fn [{:keys [steps]} _]
                             (conj steps :step2))}]
                     exception-cb-throw
                     (fn [ctx res] (is (= ctx {:steps [:step1 :mocked-injected-step1 :mocked-injected-step2 :step2]})) (done))
                     anomaly-cb-throw))))

(deftest lonely-injector-with-one-step
  (testing "Only one injector on the steps"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:inject (fn [_]
                                 {:path      [:steps]
                                  :name      "injected-step"
                                  :fn (fn [{:keys [steps]}]
                                               (conj steps :injected-step))})
                       :name   "injector1"}]
                     exception-cb-throw
                     (fn [ctx res] (is (= ctx {:steps [:injected-step]})) (done))
                     anomaly-cb-throw))))

(deftest lonely-injector-returning-nil
  (testing "If the injector doesn't return any value, it should not inject anything"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:inject (fn [_])
                       :name   "injector1"}]
                     exception-cb-throw
                     (fn [ctx res] (is (= ctx {:steps []})) (done))
                     anomaly-cb-throw))))

(deftest lonely-injector-with-multiple-steps
  (testing "Only one injector on the steps"
    (async done
      (fonda/execute {:ctx {:steps []}}
                     [{:inject (fn [_]
                                 [{:path [:steps]
                                   :name "injected-step1"
                                   :fn   (fn [{:keys [steps]}]
                                           (conj steps :injected-step1))}
                                  {:path [:steps]
                                   :name "injected-step2"
                                   :fn   (fn [{:keys [steps]} _]
                                           (conj steps :injected-step2))}])
                       :name   "injector1"}]
                     exception-cb-throw
                     (fn [ctx res]
                       (is (= ctx {:steps [:injected-step1 :injected-step2]}))
                       (done))
                     anomaly-cb-throw))))

(deftest callbacks-wrapper-fn-global-on-success
  (testing "If a callback wrapper is passed, it should be called with the value of the on-callbacks and the arguments
  that normally would be passsed to the on-callback-fns."
    (async done
      (let [processor-res 42
            on-success-event ::on-success-event
            processor-path [:success-processor-path]
            processor {:on-success (cb-with-result ::auth-success)
                       :path processor-path
                       :fn (constantly (js/Promise.resolve processor-res))}
            ]
        (fonda/execute {:callbacks-wrapper-fn (fn [cb-val ctx step-res]
                                                (is (= cb-val on-success-event))
                                                (is (= processor-res (get-in ctx processor-path) step-res))
                                                (is (= @events [[::auth-success processor-res {(first processor-path) processor-res}]]))
                                                (done))}
                       [processor]
                       exception-cb-throw
                       on-success-event)))))

(deftest callbacks-wrapper-fn-global-on-anomaly
  (testing "If a callback wrapper is passed, it should be called with the value of the on-callbacks and the arguments
  that normally would be passsed to the on-callback-fns."
    (async done
      (let [processor-anomaly (anomaly :cognitect.anomalies/incorrect)
            on-anomaly-event ::on-anomaly-event
            processor-path [:anomaly-processor-path]
            processor {:on-error (cb-with-result ::anomaly-event)
                       :path processor-path
                       :fn (constantly processor-anomaly)}]
        (fonda/execute {:callbacks-wrapper-fn (fn [cb-val ctx anomaly-res]
                                                (is (= cb-val on-anomaly-event))
                                                (is (= anomaly-res processor-anomaly))
                                                (is (= @events [[::anomaly-event anomaly-res {(first processor-path) anomaly-res}]]))
                                                (done))
                        :anomaly? true}
                       [processor]
                       exception-cb-throw
                       success-cb-throw
                       on-anomaly-event)))))

(deftest callbacks-wrapper-fn-global-on-exception
  (testing "If a callback wrapper is passed, it should be called with the value of the on-callbacks and the arguments
  that normally would be passsed to the on-callback-fns."
    (async done
      (let [processor-exception (js/Error "Bad exception")
            on-exception-event ::on-exception-event
            processor-path [:exception-processor-path]
            processor {:on-error (cb-with-result ::exception-event)
                       :path     processor-path
                       :fn       (fn [_] (throw processor-exception))}]
        (fonda/execute {:callbacks-wrapper-fn (fn [cb-val ctx exception-res]
                                                (is (= cb-val on-exception-event))
                                                (is (= exception-res processor-exception))
                                                (is (= @events [[::exception-event exception-res {}]]))
                                                (done))}
                       [processor]
                       on-exception-event
                       success-cb-throw)))))
