(ns fonda.example.simple
  (:require [clojure.string :as str]
            [fonda.core :as fonda]
            [goog.object :as gobj]))

(defn response->loads-of-data
  [ctx]
  {:before (:data ctx)
   :after (get-in ctx [:http-response :body :data])})

(defn to-str
  [data]
  (as-> data dd
    (name dd)
    (str/split dd "-")
    (str/join " " dd)))

(fonda/execute
 {}

 [{:processor      (fn [ctx]
                     {:status 200
                      :headers {"Content-Type" "application/json"}
                      :body {:data :loads-of}})
   :name           "get-loads-of-data"
   :path           [:http-response]}

  {:processor      response->loads-of-data   ;; Pure function - ctx in - ctx out
   :name           "response-processor"
   :path           [:data]}]

 {:data :very-little}

 ;; on-success
 (fn [ctx]
   (println "Before we had" (-> ctx :data :before to-str) "data,"
            "but now we have" (-> ctx :data :after to-str) "it."))

 ;; on-anomaly
 (fn [anomaly]
   (println "Anomaly detected in the matrix"))

 ;; on-exception
 (fn [exception]
   (println (gobj/get exception "message"))))
