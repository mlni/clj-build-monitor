(ns buildmonitor.ci.dummy
  (:require [clojure.tools.logging :as log]))

(defn- gen-history [n]
  (let [statuses (cycle [:success :failed :canceled])]
    (take n (map (fn [i status]
                   {:id i :status status :number i}) (iterate inc 1) statuses))))

(defn- rand-state []
  (rand-nth [:success :failed :running :canceled]))

(defn fetch-service [_]
  (log/info "Returning dummy data")
  [{:id 1 :name "Build-1" :number 42 :status :success :seconds-since 12 :history (gen-history 5)}
   {:id 2 :name "Build-2" :number 10001 :status :failed :seconds-since 12000 :history []}
   {:id 3 :name "Build-3" :number 1 :status :canceled :seconds-since 680 :history (gen-history 10)}
   {:id 4 :name "Build-4" :number 13 :status :running :seconds-since 600000 :history (gen-history 10)}
   {:id 5 :name "Build-5" :number 1234 :status (rand-state) :seconds-since 300 :history (gen-history 10)}])