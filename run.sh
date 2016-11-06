cd $(dirname "$0")
ant jar || exit
java -Xmx10m -jar dist/choogle.jar "$@"
#java -Xmx10m -classpath dist/choogle.jar org.tmotte.choogle.pagecrawl.MyDBTest "$@"
