docker-machine env dev || exit 1
eval $(docker-machine env dev)
docker-compose -f $(dirname $0)/docker-compose-postgres.yml stop || exit 1
docker-compose -f $(dirname $0)/docker-compose-postgres.yml rm -f || exit 1
docker-machine stop dev || exit 1
