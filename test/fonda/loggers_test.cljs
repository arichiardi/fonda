(ns fonda.loggers-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [orchestra-cljs.spec.test :as orchestra]
            [fonda.core :as fonda]
            [fonda.anomaly]))

(orchestra/instrument)

(defn anomaly
  ([category]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category}})
  ([category message]
   #:cognitect.anomalies{:anomaly #:cognitect.anomalies{:category category
                                                        :message  message}}))

(defn success-cb-throw [res]
  (throw (js/Error (str "not excepted success callback called with res:" res))))

(defn exception-cb-throw [err]
  (throw err))

(defn anomaly-cb-throw [anomaly]
  (throw (js/Error (str "not expected anomaly callback called with anomaly:" anomaly))))

(defn timeout-promise [milliseconds]
  (js/Promise. (fn [resolve _]
                 (js/setTimeout (fn [] (resolve)) milliseconds))))

(deftest log-anomaly-gets-steps-stack-test
  (async done
    (let [processor-res (anomaly :cognitect.anomalies/incorrect)
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] processor-res)}
          log-anomaly (fn [{:keys [stack]}]
                        (is (= (:name processor) (:name (last stack)))) (done))]
      (fonda/execute {:log-anomaly log-anomaly}
                     [processor]
                     success-cb-throw
                     (fn [_]) ;; Anomaly callbacks are tested elsewhere
                     exception-cb-throw))))

(deftest log-exception-gets-steps-stack-test
  (async done
    (let [processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] (throw (js/Error "An exception happened")))}
          log-exception (fn [{:keys [stack]}]
                          (is (= (:name processor) (:name (last stack)))) (done))]
      (fonda/execute {:log-exception log-exception}
                     [processor]
                     success-cb-throw
                     anomaly-cb-throw
                     (fn [_]))))) ;; Exception callbacks are tested elsewhere

(deftest log-success-gets-steps-stack-test
  (async done
    (let [processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] :happy-result)}
          log-success (fn [{:keys [stack]}]
                        (is (= (:name processor) (:name (last stack)))) (done))]
      (fonda/execute {:log-success log-success}
                     [processor]
                     (fn [_]) ;; Success callbacks are tested elsewhere
                     anomaly-cb-throw
                     exception-cb-throw))))

(deftest success-logger-is-non-blocking-test
  (async done
    (let [fx-atom (atom false)
          log-success-run? (atom false)
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] :a)}
          log-success (fn [_]
                        (reset! log-success-run? true)
                        (-> (timeout-promise 2000)
                            (.then #(reset! fx-atom true))))
          success-cb (fn [_]
                       (is (false? @fx-atom))
                       (is (true? @log-success-run?))
                       (done))]
      (fonda/execute {:log-success log-success}
                     [processor]
                     success-cb
                     anomaly-cb-throw
                     exception-cb-throw))))

(deftest anomaly-logger-is-non-blocking-test
  (async done
    (let [fx-atom (atom false)
          log-anomaly-run? (atom false)
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] (anomaly :cognitect.anomalies/incorrect))}
          log-anomaly (fn [_]
                        (reset! log-anomaly-run? true)
                        (-> (timeout-promise 2000)
                            (.then #(reset! fx-atom true))))
          anomaly-cb (fn [_]
                       (is (false? @fx-atom))
                       (is (true? @log-anomaly-run?))
                       (done))]
      (fonda/execute {:log-anomaly log-anomaly}
                     [processor]
                     success-cb-throw
                     anomaly-cb
                     exception-cb-throw))))

(deftest exception-logger-is-non-blocking-test
  (async done
    (let [fx-atom (atom false)
          log-exception-run? (atom false)
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] (throw (js/Error "A really bad error")))}
          log-exception (fn [_]
                          (reset! log-exception-run? true)
                          (-> (timeout-promise 2000)
                              (.then #(reset! fx-atom true))))
          exception-cb (fn [_]
                         (is (false? @fx-atom))
                         (is (true? @log-exception-run?))
                         (done))]
      (fonda/execute {:log-exception log-exception}
                     [processor]
                     success-cb-throw
                     anomaly-cb-throw
                     exception-cb))))

(deftest log-success-gets-ctx-and-stack-test
  (async done
    (let [processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] :a)}
          log-success (fn [{:keys [ctx stack]}]
                        (is (= ctx {:processor-path :a}))
                        (is (= processor (select-keys (last stack) [:path :name :processor])))
                        (done))]
      (fonda/execute {:log-success log-success}
                     [processor]
                     (fn [_]) ;; Success callbacks are tested elsewhere
                     anomaly-cb-throw
                     exception-cb-throw))))

(deftest log-anomaly-gets-stack-and-anomaly-test
  (async done
    (let [returned-anomaly (anomaly :cognitect.anomalies/incorrect)
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] returned-anomaly)}
          log-anomaly (fn [{:keys [stack anomaly]}]
                        (is (= processor (select-keys (last stack) [:path :name :processor])))
                        (is (= anomaly returned-anomaly))
                        (done))]
      (fonda/execute {:log-anomaly log-anomaly}
                     [processor]
                     success-cb-throw
                     (fn [_]) ;; Anomaly callbacks are tested elsewhere
                     exception-cb-throw))))

(deftest log-exception-gets-stack-and-exception-test
  (async done
    (let [thrown-exception (js/Error "A really bad error")
          processor {:path      [:processor-path]
                     :name      "processor name"
                     :processor (fn [_] (throw thrown-exception))}
          log-exception (fn [{:keys [stack exception]}]
                          (is (= processor (select-keys (last stack) [:path :name :processor])))
                          (is (= exception thrown-exception))
                          (done))]
      (fonda/execute {:log-exception log-exception}
                     [processor]
                     success-cb-throw
                     anomaly-cb-throw
                     (fn [_]))))) ;; Exception callbacks are tested elsewhere
