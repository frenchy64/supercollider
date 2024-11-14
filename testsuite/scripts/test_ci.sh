#!/bin/bash
# Run from repository root only. Designed to be easily reproducible
# by copy-pasting commands from failed builds.
# 
# Must set:
# - SCLANG path to sclang
# - SC_JOB_INDEX index of current test split
# - SC_JOB_TOTAL number of test splits
#
# e.g., ./testsuite/scripts/test_ci.sh sclang 0 10

set -euxo pipefail

if [ "$#" -ne 3 ]; then
  echo "Must pass 3 arguments"
  exit 2
fi

SCLANG=$1
SC_JOB_INDEX=$2
SC_JOB_TOTAL=$3
SCRIPT_ADD_PATH=testsuite/scripts/add_include_path.scd
TESTS_PATH=testsuite/classlibrary
TEST_LIST_PROTO=testsuite/scripts/test_run_proto_gha.scd
SCRIPT_RUN_TESTS=testsuite/scripts/sclang_test_runner.scd
TEST_LIST_RESULT=testsuite/scripts/run/gha_result.scxtar

$SCLANG $SCRIPT_ADD_PATH $TESTS_PATH
set +e
timeout 1200 $SCLANG $SCRIPT_RUN_TESTS $TEST_LIST_PROTO $TEST_LIST_RESULT $SC_JOB_INDEX $SC_JOB_TOTAL
set -e
RUN_EXIT=$?
# continue and print results if 0 or 1
if [ 0 -le "$RUN_EXIT" ] && [ "$RUN_EXIT" -le 1 ]; then
  exit $RUN_EXIT
fi
timeout 1m $SCLANG $SCRIPT_PRINT_RESULTS $TEST_LIST_RESULT
