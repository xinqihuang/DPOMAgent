# Isolated contract builds

Date: 2026-08-27

DPOMAgent commit `42057d061ca1363fbd52cf46dcd1aba7a1a05652` was cloned
from GitHub into a new temporary directory with no workspace siblings. Its
portability verifier passed and the complete nine-module Maven test reactor
passed: agent-web ran 226 tests with 0 failures, 0 errors and 36 conditional
skips. Contract checksum and conformance tests passed from repository-local
assets.

SREIntelligenceService commit
`139a70f01021c209c5a18eb1997b40e706c8b87e` was independently cloned into a
different temporary directory. A pre-build scan found no parent/sibling
contract reference in Maven or active scripts. The complete four-module Maven
test reactor passed: sre-web ran 248 tests with 0 failures, 0 errors and 6
conditional skips.

Both repositories pin `contracts/**` to LF so byte-level SHA-256 provenance is
stable across Windows and Unix checkouts. Neither build used the former outer
repository, a sibling service source tree or a machine-specific workspace path.
