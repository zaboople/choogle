cd $(dirname "$0")
javac -d ../build Tiny.java
java -Xss128k -Xmx3m -classpath ../build Tiny
#-XX:MetaspaceSize=1m
#-Dsun.rmi.transport.tcp.maxConnectionThreads=0

