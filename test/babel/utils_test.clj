(ns babel.utils-test
  (:require
    [errors.dictionaries :refer :all]
    [logs.utils :as log]
    [babel.processor :refer :all]
    [expectations :refer :all]))

;#########################################
;### Tests for utilties functions      ###
;### for error processing              ###
;#########################################

;; TO RUN tests, make sure you have repl started in a separate terminal

(expect "5" (print-macro-arg 5))
(expect map (print-macro-arg 'map))
(expect "\"abc\"" (print-macro-arg '"abc"))
(expect "(\"a\")" (print-macro-arg '("a")))
(expect "(+ 2 3 4 5)" (print-macro-arg '(+ 2 3 4 5)))
(expect "(2 3 4 5)" (print-macro-arg '(2 3 4 5)))
(expect "(+ (* 2 3) 9 (/ 8 9))" (print-macro-arg '(+ (* 2 3) 9 (/ 8 9))))
(expect "((+ 7 8) (* 2 3) 9)" (print-macro-arg '((+ 7 8) (* 2 3) 9)))
(expect "((+ 7 8))" (print-macro-arg '((+ 7 8))))
(expect "(+ #(* %1 3) 2)" (print-macro-arg '(+ (fn* [p1__2107#] (* p1__2107# 3)) 2)))
(expect "#()" (print-macro-arg '(fn* [] ())))
(expect "{2 3 4 5}" (print-macro-arg '{2 3 4 5}))
(expect "#{6}" (print-macro-arg '#{6}))
(expect "" (print-macro-arg 'nil))
(expect "(nil)" (print-macro-arg '(nil)))

;; print-macro-arg with :no-parens

(expect "+ 2 3 4 5" (print-macro-arg '(+ 2 3 4 5) :no-parens))
(expect "(+ 2 3 4 5)" (print-macro-arg '((+ 2 3 4 5)) :no-parens))


;; TO_DO:
;; - add cases with a quote inside an expression, the word "quote"
;; - add cases for :no-parens
