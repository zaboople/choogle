#!/bin/bash
# You should be able to run this repeatedly without shutting down.
docker-machine start dev
eval $(docker-machine env dev)
docker-compose -f $(dirname $0)/docker-compose-postgres.yml up -d
docker ps
