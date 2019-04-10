# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [master](https://github.com/arichiardi/fonda/compare/v0.2.1...HEAD) - UNRELEASED

### Changed

- Make short-circuiting for anomalies optional. [#38](https://github.com/arichiardi/fonda/pull/38)
- Change `fonda.core/execute` signature to `(fn [config steps on-exception on-success & on-anomaly])`. [#38](https://github.com/arichiardi/fonda/pull/38)

### Added

- Resolve qualified keywords to step functions. [#32](https://github.com/arichiardi/fonda/pull/32)
- Make the name key for step optional and possibly a keyword. [#33](https://github.com/arichiardi/fonda/pull/33)
- Injector steps that dynamically add steps [#34](https://github.com/arichiardi/fonda/pull/34)
- Anomaly and exception handler maps [#40](https://github.com/arichiardi/fonda/pull/40)

### Removed

- No more `:log-exception`, `:log-anomaly`, `:log-success`. [#37](https://github.com/arichiardi/fonda/pull/37)

## [v0.2.1](https://github.com/arichiardi/fonda/compare/v0.0.2...v0.2.1) - 2018-02-12

### Changed

- Remove the ctx parameter to execute. [#21](https://github.com/arichiardi/fonda/pull/21)

## [v0.0.2](https://github.com/arichiardi/fonda/compare/v0.0.1...v0.0.2) - 2018-12-13

### Changed

- Add a simple test in `examples`. [#20](https://github.com/arichiardi/fonda/pull/20)
- Introduced the notion of Log Map that is a subset of the Fonda Context passed to the logger functions.
- Added the steps stack into the Fonda Context and the Log Map. [#15](https://github.com/arichiardi/fonda/pull/16)
- Loggers are no longer blocking, and their result is ignored. [#15](https://github.com/arichiardi/fonda/pull/16)
- No default loggers anymore. [#15](https://github.com/arichiardi/fonda/pull/16)
- Move the :initial-ctx to the config and rename runtime to FondaContext. [#15](https://github.com/arichiardi/fonda/pull/16)
- Eradicate set- from the code base. [#8](https://github.com/arichiardi/fonda/pull/8)

## [v0.0.1](https://github.com/arichiardi/fonda/compare/ece2cb8...v0.0.1) - 2018-11-17

### Added

- Config with `:anomaly?`, `:log-exception`, `:log-anomaly`, `:log-success`.
- Sequence of steps that can be asynchronous or synchronous.
- Steps can be Taps or Processors.
- After running the steps, the log functions are called.
- After calling the log functions, the callback functions are called.
