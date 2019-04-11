(defproject fonda "1.0.0-SNAPSHOT"
  :url "https://github.com/arichiardi/fonda"
  :description "An async pipeline approach to functional core - imperative shell."
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :source-paths ["src"]
  :resource-paths []
  :compile-path nil
  :target-path nil
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]}

  ;; unsigned SHAPSHOTs only deployed CI
  ;; signed releases will be manually deployed
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :profiles {:dev {:test-paths ["test"]
                   :lein-tools-deps/config {:config-files [:install :user :project]
                                            :aliases [:dev :test]}}})
