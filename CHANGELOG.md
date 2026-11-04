# Changelog

## [0.4.0](https://github.com/zZHorizonZz/aquery/compare/v0.3.0...v0.4.0) (2026-11-04)


### ⚠ BREAKING CHANGES

* CelExpressionCompiler is removed in favour of CelGrammar.standard().compile(text, environment, type), and the types of the CEL module moved to sub-packages, e.g. CelFilterParser is now dev.horizon.aquery.cel.filter.CelFilterParser.

### Features

* compile CEL expressions into a typed tree ([ad34269](https://github.com/zZHorizonZz/aquery/commit/ad34269d27a9513b0c43cc575d6929876336aa53))
* make the CEL grammars configurable and split them into packages ([#26](https://github.com/zZHorizonZz/aquery/issues/26)) ([677d2e3](https://github.com/zZHorizonZz/aquery/commit/677d2e3348536c23f0913e4f006671a4ebd08337))

## [0.3.0](https://github.com/zZHorizonZz/aquery/compare/v0.2.0...v0.3.0) (2026-10-09)


### Features

* let parameter styles name their values ([d3f5478](https://github.com/zZHorizonZz/aquery/commit/d3f5478f91b945e59cf6f13eecf635384766ed25))

## [0.2.0](https://github.com/zZHorizonZz/aquery/compare/v0.1.0...v0.2.0) (2026-10-08)


### ⚠ BREAKING CHANGES

* Filter.parse is removed. Make an EbnfFilterParser or a CelFilterParser. The AIP-160 syntax tree is no longer public. FieldBackend.RestrictionContext.arg() is now value(), and the Args readers take a Filter.Value. A string on the left side of a restriction, a bare value with dots and a parenthesized argument are now refused when the text is parsed. Page token fingerprints change, thus page tokens from 0.1.0 are refused.

### Features

* support CEL filters next to AIP-160 ([d0fe433](https://github.com/zZHorizonZz/aquery/commit/d0fe43341892a27d509193148179cc0bbc3a1bf1))

## 0.1.0 (2026-09-26)


### Features

* add AIP-157 read masks ([67e40a1](https://github.com/zZHorizonZz/aquery/commit/67e40a146d85111eba1d41a3ef3fe8d5246e1e44))
* bind typed values with configurable placeholder styles ([fa8a2b5](https://github.com/zZHorizonZz/aquery/commit/fa8a2b57b27d2c2c82045494ee279c36f83a3998))
