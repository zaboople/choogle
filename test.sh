cd $(dirname "$0")
ant jar || exit
java -Xmx10m -classpath dist/choogle.jar "$@"