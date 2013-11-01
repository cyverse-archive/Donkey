(ns donkey.services.filesystem.actions
  (:require [cemerick.url :as url]
            [cheshire.core :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.data.codec.base64 :as b64]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.io :as ds]
            [deliminator.core :as deliminator]
            [donkey.services.filesystem.riak :as riak]
            [donkey.services.filesystem.validators :as validators]
            [donkey.services.garnish.irods :as filetypes]
            [ring.util.codec :as cdc]
            [clj-jargon.lazy-listings :as ll]
            [clj-icat-direct.icat :as icat])
  (:use [clj-jargon.jargon :exclude [init list-dir] :as jargon]
        [clojure-commons.error-codes]
        [donkey.util.config]
        [donkey.services.filesystem.common-paths]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.apache.tika Tika]
           [au.com.bytecode.opencsv CSVReader]
           [java.util UUID]))

(defmacro log-rulers
  [cm users msg & body]
  `(let [result# (do ~@body)]
     (when (debug-ownership)
       (->> ~users
            (map #(when (jargon/one-user-to-rule-them-all? ~cm %)
                    (jargon/log-stack-trace (str ~msg " - " % " rules all"))))
            (dorun)))
     result#))

(defn format-call
  [fn-name & args]
  (with-open [w (java.io.StringWriter.)]
    (clojure.pprint/write (conj args (symbol fn-name)) :stream w)
    (str w)))

(defn filtered-user-perms
  [cm user abspath]
  (let [filtered-users (set (conj (fs-perms-filter) user (irods-user)))]
    (filter
     #(not (contains? filtered-users (:user %1)))
     (list-user-perms cm abspath))))

(defn- list-perm
  [cm user abspath]
  {:path abspath
   :user-permissions (filtered-user-perms cm user abspath)})

(defn list-perms
  [user abspaths]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "list-perms" user abspaths)
     (validators/user-exists cm user)
     (validators/all-paths-exist cm abspaths)
     (validators/user-owns-paths cm user abspaths)
     (mapv (partial list-perm cm user) abspaths))))

(defn list-user-groups
  [user]
  "Returns a list of names for the groups a user is in.

   Parameters:
     user - the user's iRODS account name

   Returns:
     A list of group names

   Preconditions:
     clj-jargon must have been initialized

   Throws:
     ERR_NOT_A_USER - This is thrown if user is not a valid iRODS account name."
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "list-user-groups" user)
     (validators/user-exists cm user)
     (user-groups cm user))))

#_((defn path-is-dir?
   [path]
   (let [path (url-decode path)]
     (with-jargon (jargon-cfg) [cm]
       (and (exists? cm path) (is-dir? cm path)))))

(defn path-is-file?
  [path]
  (let [path (url-decode path)]
    (with-jargon (jargon-cfg) [cm]
      (and (exists? cm path) (is-file? cm path))))))



(defn- format-tree-urls
  [treeurl-maps]
  (if (pos? (count treeurl-maps))
    (json/decode (:value (first (seq treeurl-maps))) true)
    []))

(defn get-quota
  [user]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "get-quota" user)
     (validators/user-exists cm user)
     (quota cm user))))

(defn copy-path
  ([copy-map]
     (copy-path copy-map "ipc-de-copy-from"))

  ([{:keys [user from to]} copy-key]
     (with-jargon (jargon-cfg) [cm]
       (log-rulers
        cm [user]
        (format-call "copy-path" {:user user :from from :to to} copy-key)
        (validators/user-exists cm user)
        (validators/all-paths-exist cm from)
        (validators/all-paths-readable cm user from)
        (validators/path-exists cm to)
        (validators/path-writeable cm user to)
        (validators/path-is-dir cm to)
        (validators/no-paths-exist cm (mapv #(ft/path-join to (ft/basename %)) from))

        (when (some true? (mapv #(= to %1) from))
          (throw+ {:error_code ERR_INVALID_COPY
                   :paths (filterv #(= to %1) from)}))

        (doseq [fr from]
          (let [metapath (ft/rm-last-slash (ft/path-join to (ft/basename fr)))]
            (copy cm fr to)
            (set-metadata cm metapath copy-key fr "")
            (set-owner cm to user)))

        {:sources from :dest to}))))

(defn- ticket-uuids?
  [cm user new-uuids]
  (try+
    (validators/all-tickets-nonexistant cm user new-uuids)
    true
    (catch error? e false)))

(defn- gen-uuids
  [cm user num-uuids]
  (let [new-uuids (doall (repeatedly num-uuids #(string/upper-case (str (UUID/randomUUID)))))]
    (if (ticket-uuids? cm user new-uuids)
      new-uuids
      (recur cm user num-uuids)) ))

(defn add-tickets
  [user paths public?]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "add-tickets" user paths public?)
     (let [new-uuids (gen-uuids cm user (count paths))] 
       (validators/user-exists cm user)
       (validators/all-paths-exist cm paths)
       (validators/all-paths-writeable cm user paths)
       
       (doseq [[path uuid] (map list paths new-uuids)]
         (log/warn "[add-tickets] adding ticket for " path "as" uuid)
         (create-ticket cm (:username cm) path uuid)
         (when public?
           (log/warn "[add-tickets] making ticket" uuid "public")
           (doto (ticket-admin-service cm (:username cm))
             (.addTicketGroupRestriction uuid "public"))))
     
       {:user user :tickets (mapv #(ticket-map cm (:username cm) %) new-uuids)}))))

(defn remove-tickets
  [user ticket-ids]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "remove-tickets" user ticket-ids)
     (validators/user-exists cm user)
     (validators/all-tickets-exist cm user ticket-ids)

     (let [all-paths (mapv #(.getIrodsAbsolutePath (ticket-by-id cm (:username cm) %)) ticket-ids)]
       (validators/all-paths-writeable cm user all-paths)
       (doseq [ticket-id ticket-ids]
         (delete-ticket cm (:username cm) ticket-id))
       {:user user :tickets ticket-ids}))))

(defn list-tickets-for-paths
  [user paths]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "list-tickets-for-paths" user paths)
     (validators/user-exists cm user)
     (validators/all-paths-exist cm paths)
     (validators/all-paths-readable cm user paths)

     {:tickets
      (apply merge (mapv #(hash-map %1 (ticket-ids-for-path cm (:username cm) %1)) paths))})))

(defn paths-contain-char
  [paths char]
  (when-not (good-string? char)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
             :character char}))

  (apply merge (map #(hash-map %1 (not (nil? (re-seq (re-pattern char) %1)))) paths)))

(defn parent-dirs
  [user path]
  (let [pdirs (atom [])]
    (process-parent-dirs
     #(reset! pdirs (conj @pdirs %1))
     #(and (not (nil? %1))
           (not (= %1 (user-home-dir user)))) path)
    @pdirs))

(defn all-parent-dirs
  [user paths]
  (sort-by
   #(count (string/split %1 #"/")) >
   (vec (apply set/union (map #(set (parent-dirs user %1)) paths)))))

(defn looped-new-name
  "Iterates over the path, appending a _# to the end until a path that doesn't already exist is
   found."
  [cm path new-char]
  (loop [idx 0]
    (let [new-path (string/replace path #" " (str new-char "_" idx))]
      (if-not (exists? cm new-path)
        new-path
        (recur (inc idx))))))

(defn new-name
  "Creates a new name for the given path by replacing all spaces with the provided new-char.
   If the path is indicated to be a parent with the :parent flag, then the new name will NOT have
   a _# appended to it if the name already exists."
  [cm path new-char & {:keys [parent] :or {parent false}}]
  (let [new-path (string/replace path #" " new-char)]
    (if (or parent (not (exists? cm new-path)))
      new-path
      (looped-new-name cm path new-char))))

(defn has-space?
  "Returns a truthy value if the path contains a space somewhere in it."
  [path]
  (re-seq (re-pattern " ") path))

(defn move-spacey-path
  "Takes in a path and a new-char, replaces all spaces in the path with new-char, and returns a map
   with the original path as the key and the new path as the value. If the path is a parent, then
   the new name will not have a _# appended to it if it already exists."
  [cm user path new-char & {:keys [parent] :or {parent false}}]
  (when (has-space? (ft/basename path))
    (let [new-basename (new-name cm (ft/basename path) new-char :parent parent)
          new-path     (ft/path-join (ft/dirname path) new-basename)]
      (if (and (not (exists? cm new-path)) (exists? cm path))
        (move cm path new-path :user user :admin-users (irods-admins)))
      {path new-path})))

(defn fix-return-map
  [retmap new-char]
  (into {} (map #(hash-map (first %1) (string/replace (last %1) #" " new-char)) (seq retmap))))

(defn replace-spaces
  "Generates new paths by replacing all spaces with new-char."
  [user paths new-char]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "replace-spaces" paths new-char)
     (validators/user-exists cm user)
     (validators/all-paths-exist cm paths)
     (validators/user-owns-paths cm user paths)

     (when-not (good-string? new-char)
       (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
                :character new-char}))

     (let [parent-dirs (all-parent-dirs user paths)]
       (validators/user-owns-paths cm user parent-dirs)

       (let [mv-base         #(move-spacey-path cm user %1 new-char :parent false)
             mv-parent       #(move-spacey-path cm user %1 new-char :parent true)
             basename-merges (apply merge (map mv-base paths))
             parent-merges   (apply merge (map mv-parent parent-dirs))]
         {:paths (fix-return-map basename-merges new-char)})))))

(defn read-file-chunk
  "Reads a chunk of a file starting at 'position' and reading a chunk of length 'chunk-size'."
  [user path position chunk-size]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "read-file-chunk" user path position chunk-size)
     (validators/user-exists cm user)
     (validators/path-exists cm path)
     (validators/path-is-file cm path)
     (validators/path-readable cm user path)

     {:path       path
      :user       user
      :start      (str position)
      :chunk-size (str chunk-size)
      :file-size  (str (file-size cm path))
      :chunk      (read-at-position cm path position chunk-size)})))

(defn overwrite-file-chunk
  "Writes a chunk of a file starting at 'position' and extending to the length of the string."
  [user path position update-string]
  (with-jargon (jargon-cfg) [cm]
    (log-rulers
     cm [user]
     (format-call "overwrite-file-chunk" user path position update-string)
     (validators/user-exists cm user)
     (validators/path-exists cm path)
     (validators/path-is-file cm path)
     (validators/path-writeable cm user path)
     (overwrite-at-position cm path position update-string)
     {:path       path
      :user       user
      :start      (str position)
      :chunk-size (str (count (.getBytes update-string)))
      :file-size  (str (file-size cm path))})))

(defn- closest-page
  [page-positions page-number]
  (let [idx (dec page-number)
        len (count page-positions)]
    (if (<= page-number len)
      [(page-positions idx) page-number]
      [(last page-positions) len])))

(defn- csv-page-result
  [path user delim file-size chunk-size page-positions page csv]
  {:path           path
   :user           user
   :delim          (str delim)
   :file-size      (str file-size)
   :chunk-size     (str chunk-size)
   :page-positions (mapv str page-positions)
   :page           (str page)
   :csv            csv})

(defn get-csv-page
  "Retrieves a CSV page for a given chunk size. `delim` is the character that is used as a field
   separator in the file. `page-positions` is a vector of positions of pages within the file,
   which is used as an optimization when retrieving a CSV page. Without it, it would be necessary
   to sequentially scan for the requested page with every call. `page-number` is the requsted page
   number. `chunk-size` is the maximum size of a page."
  [user path delim page-positions page-number chunk-size]
  (with-jargon (jargon-cfg) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)

    (let [size       (file-size cm path)
          get-chunk  (fn [pos] (read-at-position cm path pos chunk-size))
          parse-page (fn [chunk] (deliminator/parse-excerpt chunk delim))
          get-page   (comp parse-page get-chunk)
          add-pos    (fn [ps p] (if (> p (last ps)) (conj ps p) ps))
          build-res  (partial csv-page-result path user delim size chunk-size)]
      (loop [[pos page] (closest-page page-positions page-number)
             positions  page-positions
             [csv len]  (get-page pos)]
        (let [next-pos  (+ pos len)
              positions (add-pos positions next-pos)]
          (cond (= page page-number) (build-res positions page csv)
                (< next-pos size)    (recur [next-pos (inc page)] positions (get-page next-pos))
                :else                (build-res positions page csv)))))))

(defn trim-to-line-start
  [str-chunk line-ending]
  (let [line-pos (.indexOf str-chunk line-ending)]
    (if (<= line-pos 0)
      str-chunk
      (.substring str-chunk (+ line-pos 1)))))

(defn calc-start-pos
  "Calculates the new start position after (trim-to-line-start) has been called."
  [start-pos orig-chunk trimmed-chunk]
  (+ start-pos (- (count (.getBytes orig-chunk)) (count (.getBytes trimmed-chunk)))))

(defn trim-to-last-line
  [str-chunk line-ending]
  (let [calced-pos (- (.lastIndexOf str-chunk line-ending) 1)
        last-pos   (if-not (pos? calced-pos) 1 calced-pos)]
    (.substring str-chunk 0 last-pos)))

(defn calc-end-pos
  "Calculates the new ending byte based on the start position and the current size of the chunk."
  [start-pos trimmed-chunk]
  (+ start-pos (- (count (.getBytes trimmed-chunk)) 1)))

(defn read-csv
  [separator csv-str]
  (let [ba  (java.io.ByteArrayInputStream. (.getBytes csv-str))
        isr (java.io.InputStreamReader. ba "UTF-8")]
    (mapv vec (.readAll (CSVReader. isr (.charAt separator 0))))))

(defn read-csv-chunk
  "Reads a chunk of a file and parses it as a CSV. The position and chunk-size are not guaranteed, since
   we shouldn't try to parse partial rows. We scan forward from the starting position to find the first
   line-ending and then scan backwards from the last position for the last line-ending."
  [user path position chunk-size line-ending separator]
  (with-jargon (jargon-cfg) [cm]
    (log/warn "[read-csv-chunk]" user path position chunk-size line-ending separator)
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)

    (when-not (contains? #{"\r\n" "\n"} line-ending)
      (throw+ {:error_code "ERR_INVALID_LINE_ENDING"
               :line-ending line-ending}))

    (let [chunk         (read-at-position cm path position chunk-size)
          front-trimmed (trim-to-line-start chunk line-ending)
          new-start-pos (calc-start-pos position chunk front-trimmed)
          trimmed-chunk (trim-to-last-line front-trimmed line-ending)
          new-end-pos   (calc-end-pos position trimmed-chunk)
          the-csv       (read-csv separator trimmed-chunk)]
      {:path       path
       :user       user
       :max-cols   (str (reduce #(if (>= %1 %2) %1 %2) (map count the-csv)))
       :start      (str new-start-pos)
       :end        (str new-end-pos)
       :chunk-size (str (count (.getBytes trimmed-chunk)))
       :file-size  (str (file-size cm path))
       :csv        the-csv})))
