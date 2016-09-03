docker-machine status dev || return 0
export JDBC_HOST=$(docker-machine ip dev)
export JDBC_DB="mydb"
export JDBC_USER="user"
export JDBC_PASS="pass"
