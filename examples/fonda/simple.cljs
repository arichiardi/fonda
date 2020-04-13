(ns fonda.simple
  (:require [clojure.string :as str]
            [fonda.core :as fonda]
            [fonda.core.specs]
            [goog.object :as gobj]
            [orchestra-cljs.spec.test :as orchestra]
            [process]))

(orchestra/instrument)

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

(defn main [& cli-args]
  (fonda/execute
   {:ctx {:data :very-little}}

   [{:processor      (fn [ctx]
                       {:status 200
                        :headers {"Content-Type" "application/json"}
                        :body {:data :loads-of}})
     :name           "get-loads-of-data"
     :path           [:http-response]}

    {:processor      response->loads-of-data   ;; Pure function - ctx in - ctx out
     :name           "response-processor"
     :path           [:data]}]

   ;; on-exception
   (fn [ctx exception]
     (println (gobj/get exception "message"))
     (process/exit 1))

   ;; on-success
   (fn [ctx last-step-result]
     (println "Before we had" (-> ctx :data :before to-str) "data,"
              "but now we have" (-> ctx :data :after to-str) "it.")
     (process/exit 0))))
