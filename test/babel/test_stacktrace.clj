(ns babel.test-stacktrace
  (:require
    [logs.utils :as log]
    [babel.non-spec-test :refer [to-log?]]
    [babel.utils-for-testing :as t]
    [expectations :refer :all]))

;############################################
;### Tests for functions that have specs  ###
;############################################

;; TO RUN tests, make sure you have repl started in a separate terminal

(expect #(not= % nil)  (log/set-log babel.non-spec-test/to-log?))

(expect nil (log/add-log
              (do
                (def file-name "this file")
                (:file (meta #'file-name)))))

;#########################################################
;####################  Just repl #########################
;#########################################################

(expect (t/make-pattern "Tried to divide by zero"
                        #"(.*)"
                        "Call sequence:\n"
                        "[divide (ns:clojure.lang.Numbers) called in file Numbers.java on line 188]\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(/ 9 0)"))

(expect (t/make-pattern "You have duplicated the key 1, you cannot use the same key in a hashmap twice."
                        #"(.*)"
                        "Call sequence:\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "{1 1 1 1}"))

(expect (t/make-pattern "The 'case' input 13 didn't match any of the options."
                        #"(.*)"
                        "Call sequence:\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(case (+ 6 7))"))

;#########################################################
;######### lazy seqs (class cast exception) ##############
;#########################################################

(expect (t/make-pattern "Expected a number, but a sequence was given instead."
                        #"(.*)"
                        "Call sequence:\n"
                        "[+ (ns:clojure.core) called in file core.clj on line 984]\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(+ (map #(/ 9 %) [9 0]))"))


(expect (t/make-pattern "Expected a number, but a sequence was given instead."
                        #"(.*)"
                        "Call sequence:\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(+ 2 (map #(/ 9 %) [9 0]))"))

;; Eager function, the lazy sequence gets evaluated:
(expect (t/make-pattern "Expected an integer number, but a sequence was given instead."
                        #"(.*)"
                        "Call sequence:\n"
                        "[even? (ns:clojure.core) called in file core.clj on line 1391]\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(even? (map inc [0 9]))"))

;; Eager function, the lazy sequence gets evaluated:
(expect (t/make-pattern "Tried to divide by zero"
                        #"(.*)"
                        "Call sequence:\n"
                        "[divide (ns:clojure.lang.Numbers) called in file Numbers.java on line 188]\n"
                        "[An anonymous function called dynamically]\n"
                        "[map (ns:clojure.core) called in file core.clj on line 2753]\n"
                        "[str (ns:clojure.core) called in file core.clj on line 553]\n"
                        "[str (ns:clojure.core) called in file core.clj on line 555]\n"
                        "[even? (ns:clojure.core) called in file core.clj on line 1391]\n"
                        "[Clojure interactive session (repl)]"
                        #"(\s*)")
(log/babel-test-message "(even? (map #(/ 9 %) [9 0]))"))

;#########################################################
;################ Loaded from file #######################
;#########################################################

(expect (t/make-pattern "The second argument of (map {} map) was expected to be a sequence but is a function map instead."
                        #"(.*)"
                        "Call sequence:\n"
                        "[f (ns:sample_test_files.sample2) called in file sample2.clj on line 9]\n"
                        "[g (ns:sample_test_files.sample2) called in file sample2.clj on line 11]\n"
                        "[Clojure interactive session (repl)]\n"
                        #"(\s*)")
(log/babel-test-message "(load-file \"src/sample_test_files/sample2.clj\")
                         (sample-test-files.sample2/g {} map)"))

;#########################################################
;##### lazy seqs (:print-eval-result phase) ##############
;#########################################################
