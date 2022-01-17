#!/bin/bash
#play teamA vs teamB a bunch of times
rounds=2
grep "^teamA=" gradle.properties >> tmp
grep "^teamB=" gradle.properties >> tmp
source tmp
rm tmp
rm log.log
./copypackage.sh "$teamA" "__$teamA"
./copypackage.sh "$teamB" "__$teamB"
sed -i "s/teamA=.\+/teamA=__$teamA/" gradle.properties
sed -i "s/teamB=.\+/teamB=__$teamB/" gradle.properties
for ((i=1;i<=rounds;i++))
do
  echo "running $teamA vs $teamB round $i of $rounds"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/__$teamA/Robot.java"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/__$teamB/Robot.java"
  ./gradlew run >> log.log
done
sed -i "s/teamA=.\+/teamA=__$teamB/" gradle.properties
sed -i "s/teamB=.\+/teamB=__$teamA/" gradle.properties
for ((i=1;i<=rounds;i++))
do
  echo "running $teamB vs $teamA round $i of $rounds"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/__$teamA/Robot.java"
  sed -i "s/SEED=.\+/SEED=$RANDOM;/" "src/__$teamB/Robot.java"
  ./gradlew run >> log.log
done
sed -i "s/teamA=.\+/teamA=$teamA/" gradle.properties
sed -i "s/teamB=.\+/teamB=$teamB/" gradle.properties
rm -r "src/__$teamA"
rm -r "src/__$teamB"
Awins=$(grep -c "__$teamA ([AB]) wins" log.log)
Bwins=$(grep -c "__$teamB ([AB]) wins" log.log)
games=$((Awins+Bwins))
Apercent=$((100*Awins/games))
Bpercent=$((100*Bwins/games))
echo "$teamA wins $Awins/$games = $Apercent% times and $teamB wins $Bwins/$games = $Bpercent% times"
echo "Apercent=$Apercent" >> log.log
read -n 1 -s