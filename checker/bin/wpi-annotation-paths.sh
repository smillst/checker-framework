#!/bin/sh

# Given the name of a directory containing the results produced by an
# invocation of wpi-many.sh, output the human-readable paths to the .ajava
# files to stdout.

# Usage:
#   wpi-annotation-paths TARGETDIR

TARGETDIR="$1"

if [ "$#" -ne 1 ]; then
  echo "Usage: $(basename "$0") TARGETDIR" >&2
  exit 1
fi

# First, count the number of WPI log files in the given directory.
WPI_LOG_FILE_COUNT=$(find "${TARGETDIR}"/*-wpi-stdout.log 2> /dev/null | wc -l)

if [ "$WPI_LOG_FILE_COUNT" -ne 0 ]; then
  # WPI log files exist, find the latest annotation files corresponding to
  # each of them.
  for wpi_log_file in "$TARGETDIR"/*-wpi-stdout.log;
  do
    echo "Log file: $wpi_log_file"

    # The latest match from grep should be reported, as the latest match
    # in the file corresponds to the final (and most precise) set of annotations
    # generated by whole-program inference.
    FULL_AJAVA_MATCH=$(grep -oE 'Aajava=/[^ ]+' "$wpi_log_file" | tail -1 | tr -d "'")
    AJAVA_PATH=""
    if [ -z "$FULL_AJAVA_MATCH" ]; then
      AJAVA_PATH="No .ajava files generated"
    else
      AJAVA_PATH=$(echo "$FULL_AJAVA_MATCH" | cut -d "=" -f 2 | tr -d "'" )
    fi

    echo "Annotated file(s): $AJAVA_PATH"
  done
else
  echo "No WPI log files found in $TARGETDIR"
  exit 1
fi