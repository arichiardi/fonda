(ns fonda.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [orchestra-cljs.spec.test :as orchestra]
            [fonda.core :as fonda]
            [fonda.anomaly]))

(orchestra/instrument)

(defn anomaly
  ([category]
   {fonda.anomaly/anomaly-key {:category category}})
  ([category message]
   {fonda.anomaly/anomaly-key {:category category
                               :message  message}}))

#_(execute
    {}

    {

     ;; [opt] By default, clojure anomaly
     :anomaly?      (fn [x] (:my-weird-error x))

     ;; [opt]
     :exception-tap (fn [{:keys [ctx exception step-log]}])

     :anomaly-tap   (fn [{:keys [ctx anomaly step-log]}])

     ;; TODO: PLUGGABLE DEBUGGING
     :debug         {}
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

    ;; Initial context
    {}

    ;; success cb
    (fn [result])

    ;; anomaly cb
    (fn [anomaly])

    ;; exception cb
    (fn [exception])

    )

(deftest execute-empty-chain-test-1
  (testing "Passing empty configuration with empty steps should call on-complete with a nil value."
    (async done
      (fonda/execute {} [] nil
                     (fn [res] (is (nil? res)) (done))
                     (fn [_])
                     (fn [_])))))

(deftest execute-empty-chain-test-2
  (testing "Passing a context on the configuration with empty steps should call on-complete with that context."
    (let [ctx {:initial "value"}]
      (async done
        (fonda/execute {} [] ctx
                       (fn [res] (is (= ctx res)) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest one-successful-sync-resolver-test
  (testing "Passing one synchronous resolver should call on-complete with the context augmented with the resolver result
  on the resolver path."
    (async done
      (let [resolver-res 42
            resolver-path [:resolver-path]
            resolver {:path     resolver-path
                      :name     "resolver name"
                      :resolver (fn [_] resolver-res)}]
        (fonda/execute {} [resolver] nil
                       (fn [res] (is (= resolver-res (get-in res resolver-path))) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest one-successful-sync-tap-doesnt-augment-context-test
  (testing "Passing one synchronous tap should call on-complete with the initial context"
    (async done
      (let [initial-context {:initial "context"}
            tap {:name "tap1"
                 :tap  (fn [_] :whatever-value)}]
        (fonda/execute {} [tap] initial-context
                       (fn [res] (is (= initial-context res)) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest one-successful-sync-tap-is-passed-the-runtime-context-test
  (testing "Passing one synchronous tap should call on-complete with the initial context"
    (async done
      (let [initial-context {:initial "context"}
            tap {:name "tap1"
                 :tap  (fn [{:keys [ctx]}]
                         (is (= initial-context ctx)) (done)
                         :whatever-value)}]
        (fonda/execute {} [tap] initial-context
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))

(deftest one-successful-async-resolver-test
  (testing "Passing one asynchronous resolver should call on-complete with the context augmented with the resolver result
  on the resolver path."
    (async done
      (let [resolver-res 42
            resolver-path [:resolver-path]
            resolver {:path     resolver-path
                      :name     "resolver name"
                      :resolver (fn [_] (js/Promise.resolve resolver-res))}]
        (fonda/execute {} [resolver] nil
                       (fn [res] (is (= resolver-res (get-in res resolver-path))) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest one-unsuccessful-sync-resolver-test
  (testing "Passing one synchronous unsuccessful resolver should call on-anomaly with the anomaly"
    (async done
      (let [resolver-res (anomaly :cognitect.anomalies/incorrect)
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] resolver-res)}]
        (fonda/execute {} [resolver] nil
                       (fn [_])
                       (fn [anomaly] (is (= resolver-res anomaly)) (done))
                       (fn [_]))))))

(deftest one-unsuccessful-sync-tap-test
  (testing "Passing one synchronous unsuccessful tap should call on-anomaly with the anomaly"
    (async done
      (let [tap-res (anomaly :cognitect.anomalies/incorrect)
            tap {:path [:resolver-path]
                 :name "resolver name"
                 :tap  (fn [_] tap-res)}]
        (fonda/execute {} [tap] nil
                       (fn [_])
                       (fn [anomaly] (is (= tap-res anomaly)) (done))
                       (fn [_]))))))

(deftest one-unsuccessful-sync-resolver-anomaly-tap-test
  (testing "Passing one synchronous unsuccessful resolver should call the anomaly-tap with the runtime context"
    (async done
      (let [resolver-res (anomaly :cognitect.anomalies/incorrect)
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] resolver-res)}
            anomaly-tap (fn [{:keys [anomaly]}]
                          (is (= resolver-res anomaly)) (done))]
        (fonda/execute {:anomaly-tap anomaly-tap}
                       [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))

(deftest one-unsuccessful-async-resolver-test
  (testing "Passing one asynchronous unsuccessful resolver should call on-anomaly with the anomaly"
    (async done
      (let [resolver-res (anomaly :cognitect.anomalies/incorrect)
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (js/Promise.resolve resolver-res))}]
        (fonda/execute {} [resolver] nil
                       (fn [_])
                       (fn [anomaly] (is (= resolver-res anomaly)) (done))
                       (fn [_]))))))

(deftest one-unsuccessful-async-resolver-anomaly-tap-test
  (testing "Passing one asynchronous unsuccessful resolver should call the anomaly-tap with the runtime context"
    (async done
      (let [resolver-res (anomaly :cognitect.anomalies/incorrect)
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (js/Promise.resolve resolver-res))}
            anomaly-tap (fn [{:keys [anomaly]}]
                          (is (= resolver-res anomaly)) (done))]
        (fonda/execute {:anomaly-tap anomaly-tap}
                       [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))

(deftest one-exceptional-sync-resolver-test
  (testing "Passing one synchronous exceptional resolver should call on-exception with the exception"
    (async done
      (let [resolver-res (js/Error "Bad error")
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (throw resolver-res))}]
        (fonda/execute {} [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (= resolver-res err)) (done)))))))

(deftest one-exceptional-sync-tap-test
  (testing "Passing one synchronous exceptional tap should call on-exception with the exception"
    (async done
      (let [tap-res (js/Error "Bad error")
            tap {:name     "resolver name"
                 :tap (fn [_] (throw tap-res))}]
        (fonda/execute {} [tap] nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (= tap-res err)) (done)))))))

(deftest one-exceptional-sync-resolver-error-tap-test
  (testing "Passing one synchronous exceptional resolver should call the error-tap with the runtime context"
    (async done
      (let [resolver-res (js/Error "Bad error")
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (throw resolver-res))}
            exception-tap (fn [{:keys [error]}]
                            (is (= resolver-res error)) (done))]
        (fonda/execute {:exception-tap exception-tap}
                       [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))


(deftest one-exceptional-async-resolver-test
  (testing "Passing one asynchronous exceptional resolver should call on-anomaly with the anomaly"
    (async done
      (let [resolver-res (js/Error "Bad error")
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (js/Promise.reject resolver-res))}]
        (fonda/execute {} [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (= resolver-res err)) (done)))))))

(deftest one-exceptional-async-resolver-error-tap-test
  (testing "Passing one asynchronous exceptional resolver should call the error-tap with the runtime context"
    (async done
      (let [resolver-res (js/Error "Bad error")
            resolver {:path     [:resolver-path]
                      :name     "resolver name"
                      :resolver (fn [_] (js/Promise.reject resolver-res))}
            exception-tap (fn [{:keys [error]}]
                            (is (= resolver-res error)) (done))]
        (fonda/execute {:exception-tap exception-tap}
                       [resolver] nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))

(deftest one-tap-receives-empty-log-test
  (testing "A tap should be called with an empty step-log on the runtime context"
    (async done
      (let [tap {:name     "tap name"
                 :tap (fn [{:keys [step-log]}]
                        (is (= step-log []) (done)))}]
        (fonda/execute {}
                       [tap] nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))

(deftest multiple-successful-synchronous-steps
  (testing "Passing multiple successful synchronous steps should call the on-complete callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path [:step1] :name "step1" :resolver (fn [_] step1-val)}
                        {:path [:step2] :name "step2" :resolver (fn [{:keys [step1]}] (step2-fn step1))}] nil
                       (fn [res] (is (= res {:step1 step1-val :step2 (step2-fn step1-val)})) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest multiple-successful-asynchronous-steps
  (testing "Passing multiple successful asynchronous steps should call the on-complete callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_] (js/Promise.resolve step1-val))}
                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [{:keys [step1]}]
                                     (js/Promise.resolve (step2-fn step1)))}] nil
                       (fn [res] (is (= res {:step1 step1-val :step2 (step2-fn step1-val)})) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest multiple-successful-asynchronous-and-synchronous-steps
  (testing "Passing multiple successful asynchronous and synchronous steps should call the on-complete callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc
            step3-fn str]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_] (js/Promise.resolve step1-val))}
                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [{:keys [step1]}]
                                     (step2-fn step1))}
                        {:path     [:step3]
                         :name     "step3"
                         :resolver (fn [{:keys [step2]}]
                                     (step3-fn step2))}] nil
                       (fn [res] (is (= res {:step1 step1-val
                                             :step2 (step2-fn step1-val)
                                             :step3 (-> step1-val (step2-fn) (step3-fn))})) (done))
                       (fn [_])
                       (fn [_]))))))

(deftest multiple-steps-one-unsuccessful-calls-on-anomaly-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_]
                                     (js/Promise.resolve 1))}

                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [_] unsuccessful-res)}

                        {:path     [:step3]
                         :name     "step3"
                         :resolver (fn [_] 1)}]
                       nil
                       (fn [_])
                       (fn [anomaly] (is (= unsuccessful-res anomaly)) (done))
                       (fn [_]))))))

(deftest multiple-steps-one-unsuccessful-short-circuits-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_]
                                     (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [_] unsuccessful-res)}

                        {:path     [:step3]
                         :name     "step3"
                         :resolver (fn [_] (swap! step3-counter inc))}]
                       nil
                       (fn [_])
                       (fn [anomaly] (is (and (= 1 @step1-counter)
                                              (= 0 @step3-counter))) (done))
                       (fn [_]))))))

(deftest multiple-steps-one-exceptional-calls-on-exception-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [exception (js/Error "Bad error")]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_]
                                     (js/Promise.resolve 1))}

                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [_] (throw exception))}

                        {:path     [:step3]
                         :name     "step3"
                         :resolver (fn [_] 1)}]
                       nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (= exception err)) (done)))))))

(deftest multiple-steps-one-exceptional-short-circuits-test
  (testing "Passing multiple steps, one of them unsuccessful, it short-circuits"
    (async done
      (let [exception (js/Error "Bad error")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_]
                                     (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path     [:step2]
                         :name     "step2"
                         :resolver (fn [_] (js/Promise.reject exception))}

                        {:path     [:step3]
                         :name     "step1"
                         :resolver (fn [_] (swap! step3-counter inc))}]
                       nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (and (= 1 @step1-counter)
                                          (= 0 @step3-counter))) (done)))))))

(deftest multiple-steps-one-exceptional-tap-short-circuits-test
  (testing "Passing multiple steps and one unsuccessful tap, it call short-circuits"
    (async done
      (let [exception (js/Error "Bad error")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path     [:step1]
                         :name     "step1"
                         :resolver (fn [_]
                                     (js/Promise.resolve (swap! step1-counter inc)))}

                        {:name     "step2"
                         :tap (fn [_] (js/Promise.reject exception))}

                        {:path     [:step3]
                         :name     "step1"
                         :resolver (fn [_] (swap! step3-counter inc))}]
                       nil
                       (fn [_])
                       (fn [_])
                       (fn [err] (is (and (= 1 @step1-counter)
                                          (= 0 @step3-counter))) (done)))))))

(deftest multiple-steps-tap-receives-steps-log-test
  (testing "The tap should be called with the log of the previous steps on the runtime context"
    (async done
      (let [step1-res 1
            step1 {:path     [:step1]
                   :name     "step1"
                   :resolver (fn [_] step1-res)}
            tap {:name "tap name"
                 :tap  (fn [{:keys [step-log]}]
                         (is (= step-log ["step1"])) (done))}]
        (fonda/execute {}
                       [step1
                        tap]
                       nil
                       (fn [_])
                       (fn [_])
                       (fn [_]))))))
