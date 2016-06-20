#!/bin/bash

# Fail the whole script if any command fails
set -e

export SHELLOPTS

./.travis-build-without-test.sh
diff --version

## Code style
ant check-style

## Documentation
ant javadoc-private
# Skip the manual because it cannot be compiled on Ubuntu 12.04.
# make -C checker/manual all

## Tests
# The JDK was built above; there is no need to rebuild it again.
# Don't use "-d" to debug ant, because that results in a log so long
# that Travis truncates the log and terminates the job.

## Alternative 1 (desired alternative):
# ant tests-nobuildjdk
## This should be redundant because it's run by tests-nobuildjdk
# (cd checker && ant check-compilermsgs check-purity check-tutorial)

## Alternative 2 (because alternative 1 currently crashes);
## just run tests that Travis doesn't crash on.
## Eventually, we will remove this alternative from the file.
# Run framework tests.


## end of alternatives for tests

# It's cheaper to run the demos test here than to trigger the
# checker-framework-demos job, which has to build the whole Checker Framework.
(cd checker && ant check-demos)
