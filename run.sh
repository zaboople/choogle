cd $(dirname "$0")
ant jar || exit
java -jar -Xmx10m dist/choogle.jar "$@"
