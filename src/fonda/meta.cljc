(ns fonda.meta)

;; originally from the majestic work of Michael Drogalis in onyx
;; https://github.com/onyx-platform/onyx/blob/0.14.x/src/onyx/static/util.cljc

#?(:cljs
   (defn munge-fn-name [kw]
     (str (munge-str (str (namespace kw)))
          "."
          (munge-str (str (name kw))))))

#?(:cljs
   (defn resolve-dynamic [kw]
     (js/eval (munge-fn-name kw))))

(defn kw->fn
  [kw]
  #?(:clj
     (try
       (let [user-ns (symbol (namespace kw))
             user-fn (symbol (name kw))]
         (or (ns-resolve user-ns user-fn)
             (throw (Exception.))))
       (catch Throwable e
         (throw (ex-info (str "Could not resolve symbol on the classpath, did you require the file that contains the symbol " kw "?") {:kw kw})))))
  #?(:cljs (resolve-dynamic kw)))
