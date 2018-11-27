(ns fonda.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [orchestra-cljs.spec.test :as orchestra]
            [fonda.core :as fonda]
            [fonda.anomaly]))

(orchestra/instrument)

(defn success-cb-throw [res]
  (throw (js/Error (str "not excepted success callback called with res:" res))))

(defn exception-cb-throw [err]
  (throw err))

(defn anomaly-cb-throw [anomaly]
  (throw (js/Error (str "not expected anomaly callback called with anomaly:" anomaly))))

(defn anomaly
  ([category]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category}})
  ([category message]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category
                                                        :message  message}}))

(deftest execute-empty-chain-test-1
  (testing "Passing empty configuration with empty steps should call on-success with a nil value."
    (async done
      (fonda/execute {} [] {}
                     (fn [res]
                       (is (= {} res)) (done))
                     anomaly-cb-throw
                     exception-cb-throw))))

(deftest execute-empty-chain-test-2
  (testing "Passing a context on the configuration with empty steps should call on-success with that context."
    (let [ctx {:initial "value"}]
      (async done
        (fonda/execute {} [] ctx
                       (fn [res] (is (= ctx res)) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest one-successful-sync-processor-test
  (testing "Passing one synchronous processor should call on-success with the context augmented with the processor result
  on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :name      "processor name"
                       :processor (fn [_] processor-res)}]
        (fonda/execute {} [processor] {}
                       (fn [res] (is (= processor-res (get-in res processor-path))) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest one-successful-sync-tap-doesnt-augment-context-test
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial-context {:initial "context"}
            tap {:name "tap1"
                 :tap  (fn [_] :whatever-value)}]
        (fonda/execute {} [tap] initial-context
                       (fn [res] (is (= initial-context res)) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest one-successful-sync-tap-is-passed-the-context
  (testing "Passing one synchronous tap should call on-success with the initial context"
    (async done
      (let [initial-context {:initial "context"}
            tap {:name "tap1"
                 :tap  (fn [ctx]
                         (is (= initial-context ctx)) (done)
                         :whatever-value)}]
        (fonda/execute {} [tap] initial-context
                       (fn [_])
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest one-successful-async-processor-test
  (testing "Passing one asynchronous processor should call on-success with the context augmented with the processor result
  on the processor path."
    (async done
      (let [processor-res 42
            processor-path [:processor-path]
            processor {:path      processor-path
                       :name      "processor name"
                       :processor (fn [_] (js/Promise.resolve processor-res))}]
        (fonda/execute {} [processor] {}
                       (fn [res] (is (= processor-res (get-in res processor-path))) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest one-unsuccessful-sync-processor-test
  (testing "Passing one synchronous unsuccessful processor should call on-anomaly with the anomaly"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] processor-res)}]
        (fonda/execute {} [processor] {}
                       (fn [_])
                       (fn [anomaly] (is (= processor-res anomaly)) (done))
                       exception-cb-throw)))))

(deftest one-unsuccessful-sync-tap-test
  (testing "Passing one synchronous unsuccessful tap should call on-anomaly with the anomaly"
    (async done
      (let [tap-res (anomaly :cognitect.anomalies/incorrect)
            tap {:path [:processor-path]
                 :name "processor name"
                 :tap  (fn [_] tap-res)}]
        (fonda/execute {} [tap] {}
                       success-cb-throw
                       (fn [anomaly] (is (= tap-res anomaly)) (done))
                       exception-cb-throw)))))

(deftest one-unsuccessful-sync-processor-log-anomaly-test
  (testing "Passing one synchronous unsuccessful processor should call the log-anomaly with the fonda context"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] processor-res)}
            log-anomaly (fn [{:keys [anomaly]}]
                          (is (= processor-res anomaly)) (done))]
        (fonda/execute {:log-anomaly log-anomaly}
                       [processor] {}
                       success-cb-throw
                       (fn [_])
                       exception-cb-throw)))))

(deftest one-unsuccessful-async-processor-test
  (testing "Passing one asynchronous unsuccessful processor should call on-anomaly with the anomaly"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (js/Promise.resolve processor-res))}]
        (fonda/execute {} [processor] {}
                       success-cb-throw
                       (fn [anomaly] (is (= processor-res anomaly)) (done))
                       exception-cb-throw)))))

(deftest one-unsuccessful-async-processor-log-anomaly-test
  (testing "Passing one asynchronous unsuccessful processor should call the log-anomaly with the fonda context"
    (async done
      (let [processor-res (anomaly :cognitect.anomalies/incorrect)
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (js/Promise.resolve processor-res))}
            log-anomaly (fn [{:keys [anomaly]}]
                          (is (= processor-res anomaly)) (done))]
        (fonda/execute {:log-anomaly log-anomaly}
                       [processor] {}
                       success-cb-throw
                       (fn [_])
                       exception-cb-throw)))))

(deftest one-exceptional-sync-processor-test
  (testing "Passing one synchronous exceptional processor should call on-exception with the exception"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (throw processor-res))}]
        (fonda/execute {} [processor] {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [err] (is (= processor-res err)) (done)))))))

(deftest one-exceptional-sync-tap-test
  (testing "Passing one synchronous exceptional tap should call on-exception with the exception"
    (async done
      (let [tap-res (js/Error "Bad exception")
            tap {:name "processor name"
                 :tap  (fn [_] (throw tap-res))}]
        (fonda/execute {} [tap] {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [err] (is (= tap-res err)) (done)))))))

(deftest one-exceptional-sync-processor-exception-tap-test
  (testing "Passing one synchronous exceptional processor should call log-exception with the fonda context"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (throw processor-res))}
            log-exception (fn [{:keys [exception]}]
                            (is (= processor-res exception)) (done))]
        (fonda/execute {:log-exception log-exception}
                       [processor] {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [_]))))))


(deftest one-exceptional-async-processor-test
  (testing "Passing one asynchronous exceptional processor should call on-anomaly with the anomaly"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (js/Promise.reject processor-res))}]
        (fonda/execute {} [processor] {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [err] (is (= processor-res err)) (done)))))))

(deftest one-exceptional-async-processor-log-exception-test
  (testing "Passing one asynchronous exceptional processor should call log-exception with the fonda context"
    (async done
      (let [processor-res (js/Error "Bad exception")
            processor {:path      [:processor-path]
                       :name      "processor name"
                       :processor (fn [_] (js/Promise.reject processor-res))}
            log-exception (fn [{:keys [exception]}]
                            (is (= processor-res exception)) (done))]
        (fonda/execute {:log-exception log-exception}
                       [processor] {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [_]))))))

(deftest multiple-successful-synchronous-steps-test
  (testing "Passing multiple successful synchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path [:step1] :name "step1" :processor (fn [_] step1-val)}
                        {:path [:step2] :name "step2" :processor (fn [{:keys [step1]}] (step2-fn step1))}] {}
                       (fn [res]
                         (is (= res {:step1 step1-val :step2 (step2-fn step1-val)})) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))

(deftest multiple-successful-asynchronous-steps-augmented-context-on-success-test
  (testing "Passing multiple successful asynchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_] (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [{:keys [step1]}]
                                      (js/Promise.resolve (step2-fn step1)))}] {}
                       (fn [res]
                         (is (= res {:step1 step1-val :step2 (step2-fn step1-val)})) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))


(deftest multiple-successful-asynchronous-and-synchronous-steps-test
  (testing "Passing multiple successful asynchronous and synchronous steps should call the on-success callback with the augmented context"
    (async done
      (let [step1-val 1
            step2-fn inc
            step3-fn str]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_] (js/Promise.resolve step1-val))}
                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [{:keys [step1]}]
                                      (step2-fn step1))}
                        {:path      [:step3]
                         :name      "step3"
                         :processor (fn [{:keys [step2]}]
                                      (step3-fn step2))}] {}
                       (fn [res]
                         (is (= res {:step1 step1-val
                                             :step2 (step2-fn step1-val)
                                             :step3 (-> step1-val (step2-fn) (step3-fn))})) (done))
                       anomaly-cb-throw
                       exception-cb-throw)))))


(deftest multiple-steps-one-unsuccessful-calls-on-anomaly-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_]
                                      (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] unsuccessful-res)}

                        {:path      [:step3]
                         :name      "step3"
                         :processor (fn [_] 1)}]
                       {}
                       success-cb-throw
                       (fn [anomaly] (is (= unsuccessful-res anomaly)) (done))
                       exception-cb-throw)))))

(deftest multiple-steps-one-unsuccessful-short-circuits-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [unsuccessful-res (anomaly :cognitect.anomalies/incorrect)
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] unsuccessful-res)}

                        {:path      [:step3]
                         :name      "step3"
                         :processor (fn [_] (swap! step3-counter inc))}]
                       {}
                       success-cb-throw
                       (fn [anomaly] (is (and (= 1 @step1-counter)
                                              (= 0 @step3-counter))) (done))
                       exception-cb-throw)))))

(deftest multiple-steps-one-exceptional-calls-on-exception-test
  (testing "Passing multiple steps, one of them unsuccessful, it call on-anomaly with the anomaly"
    (async done
      (let [exception (js/Error "Bad exception")]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_]
                                      (js/Promise.resolve 1))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] (throw exception))}

                        {:path      [:step3]
                         :name      "step3"
                         :processor (fn [_] 1)}]
                       {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [err] (is (= exception err)) (done)))))))

(deftest multiple-steps-one-exceptional-short-circuits-test
  (testing "Passing multiple steps, one of them unsuccessful, it short-circuits"
    (async done
      (let [exception (js/Error "Bad exception")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:path      [:step2]
                         :name      "step2"
                         :processor (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :name      "step1"
                         :processor (fn [_] (swap! step3-counter inc))}]
                       {}
                       success-cb-throw
                       anomaly-cb-throw
                       (fn [err] (is (and (= 1 @step1-counter)
                                          (= 0 @step3-counter))) (done)))))))

(deftest multiple-steps-one-exceptional-tap-short-circuits-test
  (testing "Passing multiple steps and one unsuccessful tap, it call short-circuits"
    (async done
      (let [exception (js/Error "Bad exception")
            step1-counter (atom 0)
            step3-counter (atom 0)]
        (fonda/execute {}
                       [{:path      [:step1]
                         :name      "step1"
                         :processor (fn [_]
                                      (js/Promise.resolve (swap! step1-counter inc)))}

                        {:name "step2"
                         :tap  (fn [_] (js/Promise.reject exception))}

                        {:path      [:step3]
                         :name      "step1"
                         :processor (fn [_] (swap! step3-counter inc))}]
                       {}
                       success-cb-throw
                       exception-cb-throw
                       (fn [err] (is (and (= 1 @step1-counter)
                                          (= 0 @step3-counter))) (done)))))))