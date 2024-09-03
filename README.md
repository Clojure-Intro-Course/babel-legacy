# babel-middleware

[![Clojars Project](https://img.shields.io/clojars/v/babel-middleware.svg)](https://clojars.org/babel-middleware)

:warning: **NOTICE (DEPRECATED)**: This repository is a fork of the *ancient* Babel repository owned by [elenam](https://github.com/elenam), and **is no longer being maintained** due to the messiness that comes with this project structure. All existing work on this project as of Sep 3, 2024 should be located in [this repository](https://github.com/Clojure-Intro-Course/babel) instead.

A proof of concept tool for transforming error message in Clojure into beginner friendly forms.
Very early in development, and will experience breaking changes.
Paper describing its use and development availible [here](https://github.com/Clojure-Intro-Course/mics2018demo/blob/master/mics2018.pdf "MICS Paper").

## Usage
Requires Leiningen, and Clojure 1.9.0 or greater.
Adjust your project.clj to include:
```
:dependencies [[clojure "1.10.0"]
               [babel-middleware "0.2.0-alpha"]]
:repl-options {:nrepl-middleware
              [babel.middleware/interceptor]}
```            
Launch a repl with ```lein repl```, or whichever tool you prefer. Use that repl to load your other code, or to run code.
(Has not been tested outside of Atom/proto-repl, and lein repl)


## License

Copyright © 2018

Distributed under the MIT license
