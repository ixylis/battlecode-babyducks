#!/bin/bash
#play teamA vs teamB a bunch of times
rounds=2
grep "^teamA=" gradle.properties >> tmp
grep "^teamB=" gradle.properties >> tmp
source tmp
rm tmp
rm log.log
for ((i=1;i<=$rounds;i++))
do
  echo "running $teamA vs $teamB round $i"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/$teamA/Robot.java"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/$teamB/Robot.java"
  ./gradlew run >> log.log
done
sed -i "s/teamA=.\+/teamA=$teamB/" gradle.properties
sed -i "s/teamB=.\+/teamB=$teamA/" gradle.properties
for ((i=1;i<=$rounds;i++))
do
  echo "running $teamB vs $teamA round $i"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/$teamA/Robot.java"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/$teamB/Robot.java"
  ./gradlew run >> log.log
done
sed -i "s/teamA=.\+/teamA=$teamA/" gradle.properties
sed -i "s/teamB=.\+/teamB=$teamB/" gradle.properties
Awins=$(grep -c "$teamA ([AB]) wins" log.log)
Bwins=$(grep -c "$teamB ([AB]) wins" log.log)
echo "$teamA wins $Awins times and $teamB wins $Bwins times" >> log.log