(ns embang.xlat)

(declare expression)

(defn elist
  "translates a list of expressions,
  replacing define with let"
  [es]
  (when (seq es)
    (lazy-seq
      (let [[e & es] es]
        (if (and (seq? e) (= (first e) 'define))
          (let [[name value] (rest e)]
            `((let [~name ~(expression value)]
                ~@(elist es))))
          (cons (expression e) (elist es)))))))

(defn alambda
  "translates lambda to fn"
  [[args & body]]
  `(fn
     ~(if (list? args)
        `[~@args]
        `[& ~args])
     ~@(elist body)))

(defn alet
  "translates let"
  [[bindings & body]]
  `(let [~@(mapcat (fn [[name value]]
                     [name (expression value)])
                   bindings)]
     ~@(elist body)))

(defn aif
  "translates if"
  [[& exprs :as expr]]
  (assert (#{2 3} (count exprs)) (str "illegal if: " expr))
  `(if ~@(map expression exprs)))

(defn acond [[& clauses :as expr]]
  (when (seq clauses)
    (let [[[c e] & clauses] clauses]
      (if (= c 'else)
        (do
          (assert (empty? clauses) (str "else clause must be last: " expr))
          (expression e))
        `(if ~(expression c) ~(expression e)
           ~(acond clauses))))))

(defn abegin
  "translates begin to do"
  [[& body]]
  `(do ~@(elist body)))

(defn application
  "translates function application"
  [e]
  (map expression e))

(defn expression [e]
  "translates expression"
  (if (list? e)
    (if (seq e)
      (let [[kwd & args] e]
        (case kwd
          quote  e
          lambda (alambda args)
          let    (alet args)
          if     (aif args)
          cond   (acond args)
          begin  (abegin args)
          (application e)))
      ()) ; be liberal, allow unquoted empty list
    e))

(defn dlist
  "translates directive list, replacing assume with let"
  [ds]
  (when (seq ds)
    (lazy-seq
      (let [[[kwd & args :as d] & ds] ds]
        (case kwd
          assume (let [[name value] args]
                   `((let [~name ~(expression value)]
                       ~@(dlist ds))))
          observe (cons `(~'observe ~@(map expression args)) (dlist ds))
          predict (cons `(~'predict ~@(map expression args)) (dlist ds))
          (assert false (str "illegal dlist: " d)))))))

(defn program
  "translates anglican program into clojure function"
  [& p]
  `(fn []
     ~@(dlist p)))
