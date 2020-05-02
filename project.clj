(defproject douglass/clj-psql "0.1.1-SNAPSHOT"
  :description "A small Clojure wrapper for interacting with Postgres via psql"
  :license "EPL 1.0"
  :url "https://github.com/DarinDouglass/clj-psql"
  :dependencies [[org.clojure/clojure "1.10.1"]]
  :test-paths ["test"]
  :resource-paths ["resources"]
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password  :env/clojars_password
                                    :sign-releases false}]])
