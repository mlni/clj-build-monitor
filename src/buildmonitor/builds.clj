(ns buildmonitor.builds
  (:require [clojure.data.json :as json]
            [buildmonitor.ci.teamcity :as tc]
            [buildmonitor.ci.jenkins :as jenkins]
            [clojure.tools.logging :as log]))

(def latest-builds (atom nil))

; replace with multimethod?
(comment defn- fetch-service [service-conf]
  (let [type (.toLowerCase (:type service-conf))]
    (cond (= "teamcity" type) (tc/fetch-service service-conf)
          (= "jenkins" type) (jenkins/fetch-service service-conf)
          :else (do
                  (log/warn "Unknown service type " (:type service-conf))
                  []))))

(defmulti fetch-service (fn [service] (keyword (.toLowerCase (or (:type service) "")))))
(defmethod fetch-service :teamcity [service] (tc/fetch-service service))
(defmethod fetch-service :jenkins [service] (jenkins/fetch-service service))
(defmethod fetch-service :default [service]
  (log/warn "Unknown service type " (:type service))
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
