(ns errors.prettify-exception
  (:require [clojure.string :as str]
            [errors.error-dictionary :refer :all])
  (:use [errors.dictionaries]
        [errors.messageobj]))

;; Main error processing file. Standard errors are processed by `standard` function, and
;; modified errors are processed by `prettify-exception` function.


(defn first-match
  [e-class message]
  (first (filter #(and (= (:class %) e-class) (re-matches (:match %) message))
                 error-dictionary)))

(defn get-match
  [e-class message]
  (let [match (first-match e-class message)]
    (or match (first-match "default" message))))

(defn fn-name
  "Takes a function object and returns a symbol that corresponds to the result of
   the lookup of its name.
   If no name is found, a symbol 'anonymous function' (non-conformant)
   is returned.
   Handles spec-checking functions differently since they are looked up in corefns-map
   by full name.
   Warning: 'anonymous function' symbol is non-conformant"
  [f]
  (let [f-str (str f)]
    (if (re-matches #"clojure\.spec\.test\$spec_checking_fn(.*)" f-str)
      (symbol (get-function-name f-str))
      (symbol (get-function-name (.getName (type f)))))))

(defn is-function?
  "Uses our dictionary to check if a value should be printed as a function"
  [v]
  ;; checking for nil first:
  (and v (= (get-type (.getName (type v))) "a function")))

(defn lookup-fns
  "Recursively replace internal Clojure function names with user-readable ones
   in the given value"
  [v]
  (cond
    (not (coll? v)) (if (is-function? v) (fn-name v) v)
    (vector? v) (into [] (map lookup-fns v))
    (seq? v) (into '() (reverse (map lookup-fns v)))
    (set? v) (into #{} (map lookup-fns v))
    (map? v) (reduce #(apply assoc %1 %2) {} (map lookup-fns v));; map has key/val pairs
    :else v))

(defn msg-from-matched-entry
  "Creates a message info object from an exception and its data, if exists"
  [entry message]
  (cond
    ;(and data entry) (msg-info-obj-with-data entry message data)
    entry ((:make-msg-info-obj entry) (re-matches (:match entry) message))
    :else (make-msg-info-hashes message)))

; This was added from another file and isn't needed:
(defn get-sum-text
  "concatenate all text from a message object into a string"
  [msg-obj]
  (reduce #(str %1 (:msg %2)) "" msg-obj)) ;; replace by join?

(defn process-errors
  "Takes a message from an exception as a string and returns a message object,
  to be displayed by the repl or IDE"
  [t m]
  (let [e-class (nth (re-matches #"(\w+)\.(\w+)\.(.*)" (str t)) 3)
        message (or m "") ; m can be nil
        entry (get-match e-class message)
        msg-info-obj (or (msg-from-matched-entry entry message) (make-msg-info-hashes "No message detected"))]
    {:exception-class e-class
     :msg-info-obj  msg-info-obj}))

(defn process-length
  [ex-str]
  (let [normal (re-matches #"b-length(\d+)\?" ex-str)
        greater (re-matches #"b-length-greater(\d+)\?" ex-str)
        greatereq (re-matches #"b-length-(\d+)greater\?" ex-str)
        to (re-matches #"b-length(\d+)-to-(\d+)\?" ex-str)]
        (if normal
            (str (number-word (first (rest normal))) " argument")
            (if greater
                (str (number-word (str (+ 1 (read-string (first (rest greater)))))) " or more arguments")
                (if greatereq
                    (str (number-word (first (rest greatereq))) " or more arguments")
                    (if to
                      (str (number-word (first (rest to))) " to " (number-word (first (rest (rest to)))) " arguments")
                      " no babel length data found"))))))

(defn process-another
  [functname location ex-str]
  (let [not-zero (re-matches #"b-not-0\?" ex-str)]
       (if not-zero
           (str "In function " functname ", the " location " cannot be 0.\n"))))

(defn create-spec-errors
  "Takes the message and data from a spec error and returns a modified message"
  [ex-str data]
  (let [functname (second (rest (rest (first (re-seq #"(.*)Call to (.*)/(.*) did not conform to spec(.*)" ex-str)))))
        functdata (first (:clojure.spec.alpha/problems data))
        location (first (:in functdata))
        shouldbe (second (rest (re-matches #"(.*)\/(.*)" (str (:pred functdata)))))
        wrongval (:val functdata)
        via (first (:via functdata))
        wrongvaltype (str/replace (str (type wrongval)) #"class " "")]
  (if (nil? (re-matches #"b-length(.*)" shouldbe))
      (if (nil? (re-matches #"b-(.*)" shouldbe))
          (str "In function " functname ", the " (arg-str location) " is expected to be a " (?-name shouldbe) ", but is " (get-dictionary-type wrongvaltype) wrongval " instead.")
          (str (process-another functname (arg-str location) shouldbe)))
      (str functname " can only take " (process-length shouldbe) "; received " (number-arg (str (count wrongval))) ".\n"))))

(defn process-spec-errors
  "Processes spec errors according to if they are a macro or not"
  [ex-str data notmacro]
  (let [locationdata (:clojure.spec.test.alpha/caller data)
        linenumber (str "Line: " (:line locationdata))
        sourcefile (str "\nIn: " (:file locationdata) "\n")]
  (if notmacro
    (str (create-spec-errors ex-str data) linenumber sourcefile)
    (str (create-spec-errors ex-str data)))))

(defn process-macro-errors
  "Takes the message and data from a macro error and returns a modified message"
  [err cause data]
  (let [errmap (Throwable->map err)
        specerrdata (:data errmap)
        seconderr (second (:via errmap))
        errmsg (:message seconderr)
        errclass (:type seconderr)
        linenumber (str "Line: " (:clojure.error/line data))
        columnnumber (str " Column: " (:clojure.error/column data))
        sourcefile (str "\nIn: " (:clojure.error/source data) "\n")]
        (if (nil? specerrdata)
          (str (get-all-text (:msg-info-obj (process-errors (str errclass " " errmsg)))) linenumber columnnumber sourcefile)
          (str (process-spec-errors errmsg specerrdata false) linenumber columnnumber sourcefile))))


;#########################################
;############ Location format  ###########
;#########################################

(defn line-number-format
  "Takes a line number and a character position and returns a string
   of how they are reported in an error message"
  [line ch]
  (str " on, or before, line " line))
