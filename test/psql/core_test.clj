(ns psql.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [psql.core :as sut]))

(def conn {:host (or (System/getenv "POSTGRES_HOST") "localhost")
           :port (or (System/getenv "POSTGRES_PORT") 5432)
           :username (or (System/getenv "POSTGRES_USER") "postgres")
           :password (or (System/getenv "POSTGRES_PASSWORD") "")
           :name (or (System/getenv "POSTGRES_DATABASE") "postgres")})
(def grades [{:name "Bobby" :subject "Math" :grade 80}
             {:name "Bobby" :subject "English" :grade 73}
             {:name "Suzy" :subject "Math" :grade 91}
             {:name "Suzy" :subject "English" :grade 94}])

(use-fixtures :once
  (fn [test]
    (let [command (str "create table grades("
                       "  name text,"
                       "  subject text,"
                       "  grade integer,"
                       "  comment text default null,"
                       "  constraint name_subject primary key(name, subject))")]
      (sut/execute! conn command)
      (test)
      (sut/execute! conn "drop table grades"))))

(deftest test-database-interactions
  (testing "bad operations throw errors"
    (is (thrown? clojure.lang.ExceptionInfo #"."
                 (sut/execute! conn "select * from not_exists"))))
  (testing "we can query the database"
    (is (empty? (sut/query conn :grades {}))))
  (testing "inserts work propery"
    (is (= 3 (count (sut/insert! conn :grades (butlast grades)))))
    (testing "vector commands"
      (let [{:keys [name subject grade]} (last grades)]
        (is (= 1 (sut/insert! conn ["insert into grades (name, subject, grade) values (?, ?, ?)"
                                    name subject grade])))))
    (is (= 2 (count (sut/query conn :grades {:name "Bobby"}))))
    (is (= 2 (count (sut/query conn :grades {:name "Suzy"})))))
  (testing "updates work"
    (let [condition {:name "Suzy" :subject "Math"}]
      (sut/update! conn :grades {:grade 100} condition)
      (is (= 100 (->> condition
                      (sut/query conn :grades)
                      (first)
                      (:grade)
                      (Integer/parseInt))))
      (testing "vector commands"
        (sut/update! conn ["update grades set grade = ? where name = ?" 99 "Suzy"])
        (is (= 99 (->> condition
                       (sut/query conn :grades)
                       (first)
                       (:grade)
                       (Integer/parseInt)))))))
  (testing "multi-updates work"
    (let [conditions [{:name "Suzy" :subject "Math"}
                      {:name "Bobby" :subject "English"}]]
      (sut/update! conn :grades {:comment "has tutor"} conditions)
      (is (every? (comp #{"has tutor"} :comment) (sut/query conn :grades conditions)))))
  (testing "deletes work"
    (let [condition {:comment "has tutor"}]
      (sut/delete! conn :grades condition)
      (is (empty? (sut/query conn :grades condition))))
    (testing "vector commands"
      (sut/delete! conn ["delete from grades where name = ? and subject = ?" "Bobby" "Math"])
      (is (empty? (sut/query conn :grades {:name "Bobby" :subject "Math"})))))
  (testing "multi-deletes work"
    (let [conditions [{:name "Suzy" :subject "English"}
                      {:name "Suzy" :subject "Math"}]]
      (sut/delete! conn :grades conditions)
      (is (empty? (sut/query conn :grades conditions))))))
