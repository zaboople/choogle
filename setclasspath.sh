eval $(find /d/Programs/Jetty938/lib/*.jar | xargs cygpath -wa | dblquote | xargs  classpath --reset)
echo $CLASSPATH