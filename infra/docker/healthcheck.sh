#!/usr/bin/env bash
set -euo pipefail
exec 3<>"/dev/tcp/127.0.0.1/${SERVER_PORT:?SERVER_PORT is required}"
printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r status <&3
[[ "$status" == *" 200 "* ]]
