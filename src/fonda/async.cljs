(ns fonda.async)

(defprotocol AsyncContext
  (continue [t f] [t f e]))

(defn async?
  [x]
  (satisfies? AsyncContext x))

(extend-protocol AsyncContext
  js/Promise
  (continue
    ([t f] (.then t f))
    ([t f e]
     (-> t
         (.then f)
         (.catch e)))))
