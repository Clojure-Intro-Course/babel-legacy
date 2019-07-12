(ns babel.processor
 (:require [errors.messageobj :as m-obj]
           [errors.prettify-exception :as p-exc]
           [errors.dictionaries :as d]))

;;an atom that record original error response
(def recorder (atom {:msg [] :detail []}))

(defn reset-recorder
  "This function reset the recorder atom"
  []
  (reset! recorder {:msg [] :detail []}))

(defn update-recorder-msg
  "takes an unfixed error message, and put it into the recorder"
  [inp-message]
  (swap! recorder update-in [:msg] conj inp-message))
  ;(swap! recorder assoc :msg inp-message))

(defn update-recorder-detail
  "takes error message details, and put them into the recorder"
  [inp-message]
  (swap! recorder update-in [:detail] conj inp-message))

(defn process-message
  "takes a Java Throwable object, and returns the adjusted message as a string."
  [err]
  (let [errmap (Throwable->map err)
        throwvia (:via errmap)
        viacount (count throwvia)
        errclass (str (:type (first throwvia)))
        errdata (:data errmap)]
    (if (and (= "clojure.lang.ExceptionInfo" errclass) (= viacount 1))
        (p-exc/process-spec-errors (str (.getMessage err)) errdata true)
        (if (= "clojure.lang.Compiler$CompilerException" errclass)
          (p-exc/process-macro-errors err errclass (ex-data err))
          (if (and (= "clojure.lang.ExceptionInfo" errclass) (> viacount 1))
            (str
              (->> throwvia
                   reverse
                   first
                   :message
                   (str (:type (first (reverse throwvia))) " ")
                   p-exc/process-errors
                   :msg-info-obj
                   m-obj/get-all-text)
              (p-exc/process-stacktrace err))
            (str
              (->> err
                   .getMessage
                   (str errclass " ")
                   p-exc/process-errors
                   :msg-info-obj
                   m-obj/get-all-text)
              (p-exc/process-stacktrace err)))))))


(defn macro-spec?
  "Takes an exception object. Returns a true value if it's a spec error for a macro,
   a false value otherwise."
  [exc]
  (and (> (count (:via (Throwable->map exc))) 1)
       (= :macro-syntax-check (:clojure.error/phase (:data (first (:via (Throwable->map exc))))))))

(def spec-ref {:number "a number", :collection "a sequence", :string "a string", :coll "a sequence",
                :map-arg "a two-element-vector", :function "a function", :ratio "a ratio", :future "a future", :key "a key", :map-or-vector "a map-or-vector",
                :regex "a regular expression", :num-non-zero "a number that's not zero", :arg-one "not wrong" :num "a number" :lazy "a lazy sequence"
                :wrong-path "of correct type and length", :sequence "a sequence of vectors with only 2 elements or a map with key-value pairs"})

(def length-ref {:b-length-one "one argument", :b-length-two "two arguments", :b-length-three "three arguments", :b-length-zero-or-greater "zero or more arguments",
                 :b-length-greater-zero "one or more arguments", :b-length-greater-one "two or more arguments", :b-length-greater-two "three or more arguments",
                 :b-length-zero-to-one "zero or one arguments", :b-length-one-to-two "one or two arguments", :b-length-two-to-three "two or three arguments",
                 :b-length-two-to-four "two or up to four arguments", :b-length-one-to-three "one or up to three arguments", :b-length-zero-to-three "zero or up to three arguments"})

(defn stringify
  "If there's only one item inside of path, it will use it's name via spec-ref and return a string.
   If there's two or more then it will only take the second item in the path because there's usually only three items."
  [vector-of-keywords]
  (if (= (count vector-of-keywords) 1) (name (spec-ref (first vector-of-keywords))) (name (spec-ref (second vector-of-keywords)))))

(defn has-alpha-nil?
  [error-map]
  (not (.contains (:path error-map) :clojure.spec.alpha/nil)))

(defn filter-path
   "Filters through a list of paths removing any hashmap that contains :reason or :clojure.spec.apha/nil"
   [error-maps]
   (if (> (count error-maps) 1)
       (filter #(not (contains? % :reason))
               (filter has-alpha-nil? error-maps))
       error-maps))

; (defn choose-path
;   "Returns correct path based on conditions when given a list which should be of a :clojure.spec.alpha.problems that contains a list of paths.
;    Its default case should be when the :path contains nil because of the nilable path which we never want to return because it gives null pointer.
;    Its purpose is to check for :reason which is the wrong path because of how spec is structured,
;    it removes this path through recursion until you get the correct path for the spec error."
  ; (cond
  ;   (= (count list-of-paths) 1) list-of-paths
  ;   (= (first list-of-paths) nil) {:path [:wrong-path] :pred :val :via "function" :in [0]} ;Should never happen
  ;   (.contains ((first list-of-paths) :path) :clojure.spec.alpha/nil) (choose-path (rest list-of-paths)) ;checks if path contains nil through .contains
  ;   (contains? (first list-of-paths) :reason) (choose-path (rest list-of-paths)) ;checks if path contains reason
  ;   :else (first list-of-paths))) ;return the first

(defn spec-message
  "Takes ex-info data of a spec error, returns a modified message as a string"
  [ex-data]
  (let [{my-paths :clojure.spec.alpha/problems fn-full-name :clojure.spec.alpha/fn args-val :clojure.spec.alpha/args} ex-data
        {:keys [path pred val via in]} (-> my-paths
                                           filter-path
                                           first) ;(first (filter-path my-paths))
        arg-number (first in)
        [print-type print-val] (d/type-and-val val)
        fn-name (d/get-function-name (str fn-full-name))
        function-args-val (apply str (interpose " " (map d/anonymous? (map #(second (d/type-and-val %)) args-val))))
        ]
    (if (re-matches #"corefns\.corefns/b-length(.*)" (str pred))
        (str "Wrong number of arguments, expected in " "("fn-name" "function-args-val")"  ": the function " fn-name " expects " (length-ref (keyword (d/get-function-name (str (first via))))) " but was given " (if (nil? val) 0 (count val)) " arguments") ; a for our (babel) length predicates
        (str "The " (d/arg-str arg-number) " of " "("fn-name" "function-args-val")" " was expected to be " (stringify path)
             " but is " print-type print-val " instead.\n"))))

; (defn modify-errors [inp-message]
;   (if (contains? inp-message :err)
;       (assoc inp-message :err
;              (let [err (get-error (:session inp-message))
;                    processed-error (if err (process-message err) "No detail can be found")]
;                   (do
;                     (update-recorder-detail processed-error)
;                     (update-recorder-msg (:session inp-message));(str err))
;                     ;(inp-message :err))))
;                     processed-error)))
;
;       inp-message))

(println "babel.processor loaded")
