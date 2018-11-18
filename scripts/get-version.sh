#!/usr/bin/env bash

set -euo pipefail

do_usage() {
    echo
    echo "Get the package version."
    echo
    echo "  [-S|--snapshot]  Add -SNAPHOST to the computed version."
    echo
    echo "Usage:"
    echo
    echo "  $0 [-S|--shapshot]"
    echo
    exit 1
}

snapshot=

TEMP=$(getopt -o hS -l help,snapshot -n "$0" -- "$@")
eval set -- "$TEMP"

while true ; do
    case "$1" in
        -S|--snapshot) snapshot="yes" ; shift ;;
        -h|--help) shift ; do_usage ; break ;;
        --) shift ; break ;;
        *) do_usage ; exit 1 ;;
    esac
done

package_version=$(yarn -s print-version)

if [ "$snapshot" = "yes" ]; then
    package_version+="-SNAPSHOT"
fi

echo $package_version
