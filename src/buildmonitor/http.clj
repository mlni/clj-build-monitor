(ns buildmonitor.http
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]))

(defn- encode-url [url]
  (let [u (java.net.URL. url)]
    (.toASCIIString (java.net.URI. (.getProtocol u) (.getUserInfo u) (.getHost u) (.getPort u)
                                   (.getPath u) (.getQuery u) (.getRef u)))))

(defn json-http-client [base-url & {:keys [username password]}]
  (let [options {:headers    {"Accept" "application/json"}
                 :as         :text}
        options (if (and (nil? username) (nil? password))
                  options
                  (assoc options :basic-auth [username password]))]
    (fn [href]
      (let [url (encode-url (str base-url href))]
        (log/info "Fetching " url)
        (let [promise (http/get url options)
              response @promise]
          (if (not= 2 (quot (:status response) 100))
            (throw (RuntimeException. (str "Error in HTTP request " (:status response) " " (:body response))))
            (json/read-str (:body response) :key-fn keyword)))))))