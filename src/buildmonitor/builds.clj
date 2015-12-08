(ns buildmonitor.builds
  (:require [clojure.data.json :as json]
            [buildmonitor.ci.teamcity :as tc]
            [buildmonitor.ci.jenkins :as jenkins]
            [buildmonitor.ci.dummy :as dummy]
            [clojure.tools.logging :as log]))

; Cache of last successful poll of build results
(def latest-builds (atom nil))

(defmulti fetch-service (fn [service] (keyword (.toLowerCase (or (:type service) "null")))))
(defmethod fetch-service :teamcity [service] (tc/fetch-service service))
(defmethod fetch-service :jenkins [service] (jenkins/fetch-service service))
(defmethod fetch-service :dummy [service] (dummy/fetch-service service))
(defmethod fetch-service :default [service]
  (log/warn "Unknown service type" (:type service))
  [])

(defn process-builds [conf result-fn]
  (let [builds (doall (mapcat #(fetch-service %) (:services conf)))]
    (reset! latest-builds builds)                           ; cache last result
    (result-fn builds)))

(defn background-thread-fn [broadcast-fn]
  (fn []
    (loop []
      (try
        (let [conf (json/read-str (slurp "conf.json") :key-fn keyword)]
          (try
            (#'process-builds conf broadcast-fn)
            (catch Exception e
              (log/error "Error polling for builds", e)))
          (Thread/sleep (get-in conf [:monitor :interval])))
        (catch Exception e
          (log/error "Error parsing configuration" e)
          (Thread/sleep 60000)))
      (recur))))

(defn start-polling! [broadcast-fn]
  (.start
    (Thread. ^Runnable (background-thread-fn broadcast-fn))))

(defn last-results []
  @latest-builds)
