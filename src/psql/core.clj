(ns psql.core
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- snake_case
  "Converts a string/keword value into snake_case"
  [value]
  (if (string? value)
    value
    (str/replace (name value) #"-" "_")))

(defn- wrap-parens
  "Generates a comma-separated string of values, wrapped in parens."
  [value]
  (format "(%s)" (str/join ", " value)))

(defn- boxed
  "Coerces the provided value into a sequential."
  [value]
  (if (sequential? value)
    value
    [value]))

(defn- format-value
  "Attemps to do some minimal coercion to psql formats."
  [value]
  (cond
    (string? value)
    (format "'%s'" value)

    (sequential? value)
    (wrap-parens (map format-value value))

    :else
    value))

(defn- join-kv
  "Converts a map into a form suitable for WHERE/SET clauses."
  [conditions]
  (map (fn [[key value]]
         (format "%s %s %s"
                 (snake_case key)
                 (if (sequential? value) "in" "=")
                 (format-value value)))
       conditions))

(defn- where-clause
  "Generates a WHERE clause for the given conditions."
  [conditions]
  (let [conditions (remove empty? (boxed conditions))]
    (if (empty? conditions)
      ""
      (format "where %s" (->> conditions
                              (map #(str/join " and " (join-kv %1)))
                              (map #(format "(%s)" %1))
                              (str/join " or "))))))

(defn- vector->command
  "Takes a jdbc-style vector query and converts it into a plain string query."
  [[command & params]]
  (apply format (str/replace command #"\?" "%s") (map format-value params)))

(defn- rows
  "Splits a chunk of psql output into individual rows."
  [line]
  (map str/trim (str/split line #" \| ")))

(defn- columns
  "Parses ordered column names from the first line of psql output."
  [lines]
  (map (comp keyword #(str/replace %1 #"_" "-"))
       (rows (first lines))))

(defn output->data
  "Converts psql output into clojure data."
  [output]
  (let [lines (-> output
                  (str/trim)
                  (str/split-lines))
        columns (columns lines)
        rows (map rows (butlast (drop 2 lines)))]
    (map (partial zipmap columns) rows)))

(defn execute!
  "Runs the provided command using psql."
  [conn command]
  (let [{:keys [host port name username password] :or {port 5432}} conn
        env  (merge (into {} (System/getenv))
                    (when host {"PGHOST" host})
                    (when port {"PGPORT" (str port)})
                    (when username {"PGUSER" username})
                    (when password {"PGPASSWORD" password})
                    (when name {"PGDATABASE" name}))
        {:keys [out err]} (shell/sh "psql" "--no-psqlrc"
                                    :in command :env env)]
    (when-not (str/blank? err)
      (throw (ex-info err {:conn conn :command command})))
    out))

(defn query
  "Runs a SELECT against psql and processes its output."
  ([conn command]
   (if (vector? command)
     (query conn (vector->command command))
     (output->data (execute! conn command))))
  ([conn table conditions]
   (query conn (format "select * from %s %s"
                       (snake_case table)
                       (where-clause conditions)))))

(defn insert!
  "Runs an INSERT against psql and processes its output.

  Returns the inserted rows on successful insert."
  ([conn query]
   (if (vector? query)
     (insert! conn (vector->command query))
     (let [result (->> query
                       (execute! conn)
                       (str/trim)
                       (re-matches #"INSERT \d+ (\d+)")
                       (last)
                       (Integer/parseInt))]
       (if (pos? result)
         result
         nil))))
  ([conn table data]
   (let [data (boxed data)
         columns (sort (keys (first data)))
         values (map (fn [row]
                       (reduce #(conj %1 (format-value (get row %2))) [] columns))
                     data)]
     (when (insert! conn (format "insert into %s %s values %s"
                                 (snake_case table)
                                 (wrap-parens (map snake_case columns))
                                 (str/join ", " (map wrap-parens values))))
       (query conn table data)))))

(defn delete!
  "Runs a DELETE against psql and processes its output."
  ([conn query]
   (if (vector? query)
     (delete! conn (vector->command query))
     (let [result (->> query
                       (execute! conn)
                       (str/trim)
                       (re-matches #"DELETE (\d+)")
                       (last)
                       (Integer/parseInt))]
       (if (pos? result)
         result
         nil))))
  ([conn table conditions]
   (delete! conn (format "delete from %s %s"
                         (snake_case table)
                         (where-clause conditions)))))

(defn update!
  "Runs an UPDATE against psql and processes its output.

  Returns the updated rows on succesful update."
  ([conn query]
   (if (vector? query)
     (update! conn (vector->command query))
     (let [result (->> query
                     (execute! conn)
                     (str/trim)
                     (re-matches #"UPDATE (\d+)")
                     (last)
                     (Integer/parseInt))]
     (if (pos? result)
       result
       nil))))
  ([conn table updates conditions]
   (when (update! conn (format "update %s set %s %s"
                               (snake_case table)
                               (str/join ", " (join-kv updates))
                               (where-clause conditions)))
     (query conn table conditions))))
