#!/bin/bash
#run current version vs sprint and update if better
threshold=55 #minimum win percent to update
source name.txt
echo "name=$name"
sed -i "s/teamA=.\+/teamA=$name/" gradle.properties
sed -i "s/teamB=.\+/teamB=sprint/" gradle.properties
sed -i "s/rounds=.\+/rounds=1/" test.sh
echo "Running tests"
./test.sh
grep "^Apercent=" log.log >> tmp
source tmp
rm tmp
echo "Winrate = $Apercent%"
if [ $Apercent -gt $threshold ]
then
  echo "Above threshold; updating"
  rm -r src/reference
  ./copypackage.sh sprint reference
  rm -r src/sprint
  ./copypackage.sh "$name" sprint
  echo "Updated"
else
  echo "Below threshold"
fi
read -n 1 -s