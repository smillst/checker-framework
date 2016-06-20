#!/bin/bash

# Fail the whole script if any command fails
set -e

export SHELLOPTS

./.travis-build-without-test.sh
ulimit -a
ulimit -s 1082768  # Require a bit of more memory.


# It's cheaper to run the demos test here than to trigger the
# checker-framework-demos job, which has to build the whole Checker Framework.
(cd checker && ant check-demos)
