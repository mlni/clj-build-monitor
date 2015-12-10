(ns buildmonitor.core
  (:use org.httpkit.server
        (compojure [core :only [defroutes GET POST]]
                   [route :only [files not-found resources]]
                   [handler :only [site]]))
  (:require [ring.middleware.reload :as reload]
            [ring.util.response :as resp]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [buildmonitor.builds :as build])
  (:gen-class))

(def connections (atom {}))

(defn- send-builds [channel builds]
  (when builds
    (send! channel (json/json-str builds))))

(defn push-handler [req]
  (with-channel req chn
                (swap! connections assoc chn true)
                (log/debug "New connection, total: " (count @connections))
                (on-close chn (fn [status]
                                (log/debug "Lost connection, total: " (count @connections))
                                (swap! connections dissoc chn)))
                (on-receive chn (fn [_]))
                ; send cached builds
                (send-builds chn (build/last-results))))

(defroutes app
           (GET "/" [] (resp/redirect "index.html"))
           (GET "/ws" [] push-handler)
           (resources "/" {:root "/public"})
           (not-found "Page not found"))

(defn process-builds [last-result]
  (doseq [conn (keys @connections)]
    (send-builds conn last-result)))


(defn -main [& args]
  (let [handler (reload/wrap-reload (site #'app))
        port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (log/info "Starting server on port " port)
    (run-server handler {:port port})
    (log/info "... started"))
  (build/start-polling! process-builds))
