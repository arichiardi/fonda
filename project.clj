(defproject com.elasticpath/fonda "0.0.1"
  :url "https://github.com/elasticpath/fonda"
  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths []
  :compile-path nil
  :target-path nil
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]
                           :aliases [:dev :test]}
  :repositories [["releases" {:username :env/clojars_username
                              :password :env/clojars_password}]])