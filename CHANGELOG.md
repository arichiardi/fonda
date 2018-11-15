# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [master](https://github.com/elasticpath/terraform-vars/compare/ece2cb8...HEAD) (unreleased)

### Added

- Config with :anomaly?, :log-exception, :log-anomaly, :log-success
- Sequence of steps that can be asynchronous or synchronous
- Steps can be Taps or Processors
- After running the steps, the log functions are called
- After calling the log functions, the callback functions are called
