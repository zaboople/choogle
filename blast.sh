allRuns=100
oneRun=50
dd=$(date)
for (( all=1; all <= $allRuns; all++ ))
do
  for (( i=1; i <= $oneRun; i++ ))
  do
    (curl --silent http://localhost:8080/?x=$all"%20"$i) &
  done
  wait
done
echo "$dd"
date