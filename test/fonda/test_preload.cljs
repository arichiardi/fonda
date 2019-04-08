(ns fonda.test-preload
  (:require [cljs.spec.alpha :as s]
            [cljs.test :as test]
            [expound.alpha :as expound]))

(set! s/*explain-out* (expound/custom-printer {:theme :figwheel-theme}))
