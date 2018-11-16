#!/usr/bin/env bash

PACKAGE_VERSION=$(sed -nE 's/^\s*"version": "(.*?)",$/\1/p' package.json)
echo $PACKAGE_VERSION