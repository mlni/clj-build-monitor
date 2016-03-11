(ns buildmonitor.ci.teamcity
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [buildmonitor.http :as http]
            [buildmonitor.util :as u]))

(defn- parse-status [build]
  (cond (:running build) :running
        (:canceledInfo build) :canceled
        :else (get {"SUCCESS" :success
                    "FAILURE" :failed
                    "ERROR"   :failed
                    "UNKNOWN" :canceled} (:status build))))

(defn- parse-start-time [build]
  ; 20151206T145920+0000
  (let [start-time (tf/parse (tf/formatter "yyyyMMdd'T'HHmmssZ") (:startDate build))
        now (if (t/after? start-time (t/now)) start-time (t/now))] ; clocks are not always in sync
    (t/in-seconds (t/interval start-time now))))

(defn- simplify-teamcity-build [build build-history build-conf]
  {:id            (u/->id (str (:buildTypeId build) (:number build)))
   :name          (or (:title build-conf)
                      (get-in build [:buildType :name]))
   :number        (:number build)
   :status        (parse-status build)
   :seconds-since (parse-start-time build)
   :history       (map (fn [b] {:id     (u/->id (str (:buildTypeId b) (:number b)))
                                :number (:number b)
                                :status (parse-status b)})
                       (take 10 build-history))})

(defn- sort-by-build-number [& all-builds]
  (let [build-history (mapcat :build all-builds)]
    (reverse (sort-by #(bigint (:number %)) build-history))))

(defn- fetch-build [client build-conf]
  (let [build-history (client (str "/httpAuth/app/rest/buildTypes/id:" (:id build-conf) "/builds?locator=count:10,failedToStart:any"))
        running-builds (client (str "/httpAuth/app/rest/buildTypes/id:" (:id build-conf) "/builds?locator=running:true,count:10"))
        sorted-builds (sort-by-build-number build-history running-builds)
        build-details (client (get (first sorted-builds) :href))]
    (simplify-teamcity-build build-details (rest sorted-builds) build-conf)))

(defn fetch-service [service]
  (let [client (http/json-http-client (:url service) :username (:username service) :password (:password service))]
    (map #(fetch-build client %) (:builds service))))
