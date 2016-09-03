docker-machine start dev
eval $(docker-machine env dev)
docker-compose -f $(dirname $0)/docker-compose-postgres.yml up -d
