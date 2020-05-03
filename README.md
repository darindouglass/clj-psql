# clj-psql

[![Clojars Project](https://img.shields.io/clojars/v/douglass/clj-psql.svg)](https://clojars.org/douglass/clj-psql)

A small wrapper around `psql`. Intended for use with [babashka](https://github.com/borkdude/babashka) by those that don't want to maintain their own JDBC-enabled binary.

## Usage

The library exposes 4 main fns:

- `insert!`
- `query`
- `update!`
- `delete!`

Each fn has two arities: one that takes in connection config and a string query and one that provides a more [`next.jdbc`-esque](https://github.com/seancorfield/next-jdbc/) interface.

Each example below assumes the following schema:

```postgres
create table grades(
  name text,
  subject text,
  grade integer,
  comment text default 'N/A',
  constraint name_subject primary key(name, subject));
```

and the following clojure setup:

```clojure
user> (require '[psql.core :as psql])
user> (def conn {:host "localhost"
                 :name "mydb"
                 :username "user"
                 :password "secret"})
```

### `insert!`
#### Simple form
Runs the provided command and returns the number of rows inserted:

```clojure
user> (psql/insert! conn "insert into grades (name, subject, grade) values ('Bobby Tables', 'Math', 100)")
1
```

#### JDBC form
Takes in table name as a keyword and a vector (or map if only inserting one row) of data. Returns the inserted rows from the database:

```clojure
user> (psql/insert! conn :grades [{:name "Bobby Tables" :subject "English" :grade 72}
                                  {:name "Suzy Butterbean" :subject "Math" :grade 100}
                                  {:name "Suzy Butterbean" :subject "English" :grade 87}])
({:name "Bobby Tables",
  :subject "English",
  :grade "72",
  :comment "N/A"}
 {:name "Suzy Butterbean",
  :subject "Math",
  :grade "100",
  :comment "N/A"}
 {:name "Suzy Butterbean",
  :subject "English",
  :grade "87",
  :comment "N/A"})
```

### `query`
#### Simple form
Runs the provided `SELECT` statement and parses the data into a vector of maps:

```clojure
user> (psql/query conn "select name, subject from grades where grade = 100")
({:name "Bobby Tables", :subject "Math"}
 {:name "Suzy Butterbean", :subject "Math"})
```

#### JDBC form
Takes in the table name as a keyword and a some conditions:

```clojure
;; Map of conditions. All non-sequentials are assumed to be equality checks.
user> (psql/query conn :grades {:name "Bobby Tables"})
({:name "Bobby Tables",
  :subject "Math",
  :grade "100",
  :comment "N/A"}
 {:name "Bobby Tables",
  :subject "English",
  :grade "72",
  :comment "N/A"})
;; Sequentials within the conditions are assumed to be IN checks
user> (psql/query conn :grades {:grade [72 100]})
({:name "Bobby Tables",
  :subject "Math",
  :grade "100",
  :comment "N/A"}
 {:name "Bobby Tables",
  :subject "English",
  :grade "72",
  :comment "N/A"}
 {:name "Suzy Butterbean",
  :subject "Math",
  :grade "100",
  :comment "N/A"})
;; Sequentials OF maps are assumed to be discrete sets of conditions and are joined by `OR`
user> (psql/query conn :grades [{:grade [72]}
                                {:name "Suzy Butterbean" :grade 100}])
({:name "Bobby Tables",
  :subject "English",
  :grade "72",
  :comment "N/A"}
 {:name "Suzy Butterbean",
  :subject "Math",
  :grade "100",
  :comment "N/A"})
```

## `update!`
#### Simple form
Runs the provided `UPDATE` statement and returns the number of rows updated:

```clojure
user> (psql/update! conn "update grades set comment = 'null' where name = 'Bobby Tables'")
2
```
#### JDBC form
Takes the table name as a keyword, a map of new values to be applied to the matched rows, and some conditions. Returns the updated rows from the database:

```clojure
user> (psql/update! conn :grades {:comment "moving soon"} {:name "Bobby Tables"})
({:name "Bobby Tables",
  :subject "Math",
  :grade "100",
  :comment "moving soon"}
 {:name "Bobby Tables",
  :subject "English",
  :grade "72",
  :comment "moving soon"})
```

## `delete!`
#### Simple form
Runs the provided `DELETE` statement and returns the number of rows removed:

```clojure
user> (psql/delete! conn "delete from grades where name = 'Bobby Tables'")
2
```

#### JDBC form
Takes the table name as a keyword and conditions. Returns the number of rows removed from the database:

```clojure
user> (psql/delete! conn :grades {:subject "English"})
1 
```

## License

Copyright © 2020 Darin Douglass

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
