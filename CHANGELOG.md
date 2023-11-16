Changelog
=========

1.2.0
-----

_2023-11-16_

- **New:** Support for property builders.
- Update to Kotlin `1.9.20`.
- Update to AutoValue `1.10.4`.
- Update to KotlinPoet `1.14.2`.
- Update to Moshi `1.15.0`.
- Update to Okio `3.6.0`.

1.1.3
-----

_2021-11-15_

* **Fix:** Don't leak nested enums across multiple AVK extension passes.
* **Fix:** Add missing `@JsonClass` annotation to nested enums.
* **Fix:** Generate constructor parameter defaults for `@JsonClass`-annotated classes.

1.1.2
-----

_2021-11-05_

* **Enhancement:** Skip processing if there are no `@AutoValue`-annotated classes in the current round.
* **Enhancement:** Report an error if there are no `@AutoValue`-annotated classes across all rounds.
* **Fix:** Use correct classloader when loading `AutoValueExtension` services

1.1.1
-----

_2021-11-03_

* **Fix:** `AutoValueKotlinProcessor` now properly declares its supported options.

1.1.0
-----

_2021-11-02_

The implementation has been reworked to run as a standalone processor that then runs AutoValue
_within_ its own process. This allows us to share some data across all processed types and expand
the supported feature set.

As a result...
* **New:** Support for converting nested AutoValue classes!
* **New:** (Basic) support for converting nested enum classes!

1.0.2
-----

_2021-10-25_

* Implicit `kotlin.*` package imports are now stripped since they're redundant.

1.0.1
-----

_2021-10-22_

* Internal refactoring, nothing public-facing changed in this.

1.0.0
-----

_2021-10-21_

Initial release!
