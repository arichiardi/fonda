(ns fonda.async)

(defprotocol Async)

(defn async?
  [x]
  (satisfies? Async x))
