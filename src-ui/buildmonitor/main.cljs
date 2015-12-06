(ns buildmonitor.main
  (:require [cljs.core.async :refer [chan <! >! put! close!]]
            [reagent.core :as r]
            [buildmonitor.socket :as ws])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defonce connected (r/atom false))
(defonce builds (r/atom []))

(defn log [& msgs]
  (.log js/console (apply str msgs)))

(log "Starting up")

(def connection-channel (chan))
(def read-channel (chan))

(ws/open-socket "/ws" connection-channel read-channel)
(go-loop []
         (let [msg (<! read-channel)]
           (when msg
             (reset! builds (.parse js/JSON msg))
             (recur))))

(go-loop []
         (let [state (<! connection-channel)]
           (when state
             (reset! connected (:connected state))
             (recur))))


(defn render-build [build]
  [:li.build {:className (aget build "status")}
   [:div.title (aget build "name")]
   [:div.meta
    [:span.number (aget build "number")]
    [:ol.history
     (for [h (aget build "history")]
       ^{:key (aget h "id")} [:li (if (= (aget h "status") "success") "*" "!")])]]])

(defn- connection-status [connected]
  (when-not connected
    [:div.connection
     [:div.overlay-background]
     [:div.overlay-message
      "Disconnected"]]))

(defn build-results []
  [:div
   [:ol.builds
    (for [build @builds]
      ^{:key (aget build "id")}
      [render-build build])]
   [connection-status @connected]])

(r/render [build-results]
          (js/document.getElementById "app"))

(log "started up")
