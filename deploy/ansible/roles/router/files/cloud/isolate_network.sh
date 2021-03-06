#!/bin/bash -e

TEAM=${1?Usage: isolate_network.sh <team>}

if ! [[ $TEAM =~ ^[0-9]+$ ]]; then
 echo "Team shold be integer"
 exit 1
fi

iptables -w -A FORWARD -o "eth0.$((TEAM+100))" -j DROP
