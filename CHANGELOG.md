# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Added
- Add clj-kondo config to classpath for export.
- Add support for using io.opentracing.tag in `set-tags`.
- Add support for keywords in `set-tags`.

## [0.2.1] - 2020-02-09
### Fixed
- Fixed TextMap inject/extract split (opentracing v0.32.0) breaking propagation.

## [0.2.0] - 2019-10-26
### Added
- Added convenience functions `scope-manager` and `activate`.

### Changed
- Dependency updates
  - opentracing 0.32.0 to 0.33.0
- Replaced deprecated `opentracing-clj.span-builder/start`. No longer accepts
  `finish-on-close?`. Spans started with this function now follow the
  opentracing directive of disallowing automatic `Span` finish upon `Scope`
  close.

## [0.1.5] - 2019-09-08
### Fixed
- Fix `:opentracing.span-data/child-of` spec.

## [0.1.4] - 2019-06-17
### Changed
- Dependency updates
  - opentracing 0.31.0 to 0.32.0
  - ring 1.6.3 to ring-core 1.7.1

### Fixed
- Nesting `wrap-opentracing` middleware will no longer create multiple spans.

## [0.1.3] - 2019-03-25
### Fixed
- Fix `with-span` to use the existing span behaviour when the initializing spec is ambiguous.

## [0.1.2] - 2018-12-17
### Added
- Existing spans can now be passed to `with-span`.

### Fixed
- Codox source-uri

## [0.1.0] - 2018-09-17
### Added
- Core functions for creating and manipulating spans.
- Middleware for instrumenting Ring.
- Functions for handling span context propagation.

[Unreleased]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.2.1...HEAD
[0.2.0]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.2.0...0.2.1
[0.2.0]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.1.5...0.2.0
[0.1.5]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.1.4...0.1.5
[0.1.4]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/alvinfrancis/opentracing-clj/compare/v0.1.0...v0.1.2
[0.1.0]: https://github.com/alvinfrancis/opentracing-clj/compare/284ca4ca0bfadf860c46403c69fd0b313128e6ed...v0.1.0
