(ns fonda.async)

(defprotocol AsyncContext
  (continue [t f]))

(defn async?
  [x]
  (satisfies? AsyncContext x))

(extend-protocol AsyncContext
  js/Promise
  (continue [t f] (.then t f)))
