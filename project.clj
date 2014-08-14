(defproject influxdb-proxy "0.1.0-SNAPSHOT"
    :description "FIXME: write description"
    :url "http://example.com/FIXME"
    :license {:name "Eclipse Public License"
              :url "http://www.eclipse.org/legal/epl-v10.html"}
    :dependencies [
        [org.clojure/clojure "1.6.0"]
        [http-kit "2.1.16"]
        [org.clojure/data.json "0.2.5"] ;; https://github.com/clojure/data.json
        [capacitor "0.2.2"] ;; https://github.com/olauzon/capacitor
        [compojure "1.1.8"] ;; https://github.com/weavejester/compojure
        [javax.servlet/servlet-api "2.5"]
        [ring/ring-devel "1.3.0"]
        [ring/ring-core "1.3.0"]
        [ring-cors "0.1.0"]
        [riemann "0.2.6"]
    ]
    :main ^:skip-aot influxdb-proxy.core
    :target-path "target/%s"
    :profiles {:uberjar {:aot :all}}
)
