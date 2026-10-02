#!/bin/bash
# Script to load environment variables from .env file

if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
    echo "Environment variables loaded from .env"
else
    echo "Warning: .env file not found"
    exit 1
fi
