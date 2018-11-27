# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [master](https://github.com/elasticpath/fonda/compare/v0.0.1...HEAD) (unreleased)

### Changed

- Introduced the notion of Log Map that is a subset of the Fonda Context passed to the logger functions
- Added the steps stack into the Fonda Context and the Log Map [#15](https://github.com/elasticpath/fonda/pull/16)
- Loggers are no longer blocking, and their result is ignored [#15](https://github.com/elasticpath/fonda/pull/16)
- No default loggers anymore [#15](https://github.com/elasticpath/fonda/pull/16)
- Move the :initial-ctx to the config and rename runtime to FondaContext [#15](https://github.com/elasticpath/fonda/pull/16)
- Eradicate set- from the code base [#8](https://github.com/elasticpath/fonda/pull/8)

## [v0.0.1](https://github.com/elasticpath/fonda/compare/ece2cb8...v0.0.1) (2018-11-17)

### Added

- Config with `:anomaly?`, `:log-exception`, `:log-anomaly`, `:log-success`.
- Sequence of steps that can be asynchronous or synchronous.
- Steps can be Taps or Processors.
- After running the steps, the log functions are called.
- After calling the log functions, the callback functions are called.