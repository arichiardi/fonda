(defn throw-if-non-zero
  [process]
  (let [exit-code (.waitFor process)]
    (if-not (= 0 exit-code)
      (throw (ex-info (str "Process returned non-zero exit code (" exit-code ")")
                      {:exit-code exit-code}))
      process)))

(defn publish-shapshot?
  []
  (= "true" (System/getenv "PUBLISH_SNAPSHOT")))

;(def +version+
;  (-> (ProcessBuilder. (into ["scripts/get-version.sh"]
;                             (when publish-shapshot? ["--snapshot"])))
;      (.start)
;      (throw-if-non-zero)
;      (.getInputStream)
;      slurp
;      (clojure.string/trim-newline)))
(def +version+ "0.0.2")

(defproject com.elasticpath/fonda +version+
  :url "https://github.com/elasticpath/fonda"
  :description "An async pipeline approach to functional core - imperative shell."
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths []
  :compile-path nil
  :target-path nil
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]
                           :aliases [:dev :test]}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]])
