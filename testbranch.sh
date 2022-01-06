#!/bin/bash
#play two branches against each other. With one argument plays the named branch against the current branch. With zero arguments plays main against the current branch.

mkdir tmp 2>/dev/null
currentBranch=$(git symbolic-ref --short HEAD)
if [ ! -z $2 ]
then
git checkout $2 2>/dev/null
fi
teamA=$(echo ${1-main} | sed "s/-//g")
teamB=$(echo ${2-$currentBranch} | sed "s/-//g")
./copypackage.sh josh $teamA
cp -r src/$teamA tmp
rm -r src/$teamA
git checkout ${2-$currentBranch} 2>/dev/null >/dev/null 
./copypackage.sh josh $teamB
cp -r tmp/$teamA src
git checkout $currentBranch 2>/dev/null >/dev/null
sed "s/teamA=.\+/teamA=$teamA/" gradle.properties | sed "s/teamB=.\+/teamB=$teamB/" > aaa.txt
mv aaa.txt gradle.properties
grep "maps=" gradle.properties
echo running $teamA vs $teamB
./gradlew run >> log.log
sed "s/teamA=.\+/teamA=$teamB/" gradle.properties | sed "s/teamB=.\+/teamB=$teamA/" > aaa.txt
mv aaa.txt gradle.properties 
echo running $teamB vs $teamA
./gradlew run >> log.log
rm -r src/$teamA
rm -r src/$teamB
rm -r tmp
