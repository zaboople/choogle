cd $(dirname "$0")
ant jar || exit
java -jar -Xmx200m dist/choogle.jar "$@"