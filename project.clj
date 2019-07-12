(defproject buildmonitor "0.1.0"
  :description "Clojure build monitor for displaying CI status on a big screen"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/data.json "0.2.3"]
                 [reagent "0.5.1"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-devel "1.1.8"]
                 [compojure "1.0.2"]
                 [http-kit "2.1.19"]
                 [clj-time "0.11.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [javax.xml.bind/jaxb-api "2.3.0"]          ; java 11
                 [com.sun.xml.bind/jaxb-core "2.3.0"]
                 [com.sun.xml.bind/jaxb-impl "2.3.0"]
                 [log4j/log4j "1.2.17"]]
  :min-lein-version "2.0.0"
  :source-paths ["src", "src-ui"]
  :main buildmonitor.core
  :aot [buildmonitor.core]
  :plugins [[lein-cljsbuild "1.1.1"]]
  :clean-targets ^{:protect false} [:target-path "resources/public/main.js"]
  :hooks [leiningen.cljsbuild]
  :uberjar-name "clj-build-monitor.jar"

  :profiles {:uberjar {:cljsbuild {:builds [{:source-paths ["src-ui"]
                                             :compiler     {:output-to     "resources/public/main.js"
                                                            :optimizations :advanced
                                                            :pretty-print  false}}]}}
             :dev     {:cljsbuild {:builds [{:source-paths ["src-ui"]
                                             :compiler     {:output-to     "resources/public/main.js"
                                                            :optimizations :whitespace
                                                            :pretty-print  true}}]}}
             })
