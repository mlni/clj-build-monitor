(ns buildmonitor.builds
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]))

(def latest-builds (atom nil))

(defn- parse-status [build]
  (cond (:running build) :running
        (:canceledInfo build) :canceled
        :else (get {"SUCCESS" :success
                    "FAILURE" :failed
                    "ERROR"   :failed
                    "UNKNOWN" :canceled} (:status build))))

(defn- status-text [status]
  (get {:running  "Running"
        :canceled "Canceled"
        :failed   "Failed"
        :success  "Success"} status))

(defn- ->id [string]
  (str (.hashCode string)))

(defn- simplify-teamcity-build [build build-history build-conf]
  {:id          (->id (str (:buildTypeId build) (:number build)))
   :name        (or (:title build-conf)
                    (get-in build [:buildType :name]))
   :number      (:number build)
   :status      (parse-status build)
   :status-text (status-text (parse-status build))
   :history     (map (fn [b] {:id     (->id (str (:buildTypeId b) (:number b)))
                              :number (:number b)
                              :status (parse-status b)})
                     (take 10 build-history))})

(defn- sort-by-build-number [& all-builds]
  (let [build-history (mapcat :build all-builds)]
    (reverse (sort-by #(bigint (:number %)) build-history))))

(defn- fetch-build [client build-conf]
  (let [build-history (client (str "/httpAuth/app/rest/buildTypes/id:" (:id build-conf) "/builds?locator=count:10"))
        running-builds (client (str "/httpAuth/app/rest/buildTypes/id:" (:id build-conf) "/builds?locator=running:true,count:10"))
        sorted-builds (sort-by-build-number build-history running-builds)
        build-details (client (get (first sorted-builds) :href))]
    (simplify-teamcity-build build-details (rest sorted-builds) build-conf)))

(defn- make-client [service]
  (let [base-url (:url service)
        options {:basic-auth [(:username service)
                              (:password service)]
                 :headers    {"Accept" "application/json"}
                 :as         :text}]
    (fn [href]
      (let [url (str base-url href)]
        (log/info "Fetching " url)
        (let [promise (http/get url options)
                    response @promise]
          (json/read-str (:body response) :key-fn keyword))))))

(defn- fetch-service [service]
  (let [client (make-client service)]
    (map #(fetch-build client %) (:builds service))))

(defn- fetch-builds [conf]
  (let [builds (mapcat #(fetch-service %) (:services conf))]
    builds))

(defn process-builds [conf result-fn]
  (let [builds (doall (fetch-builds conf))]
    (reset! latest-builds builds)                           ; cache last result
    (result-fn builds)))

(defn start-polling! [broadcast-fn]
  (.start
    (Thread. (fn []
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
                 (recur))))))

(defn last-results []
  @latest-builds)
