#!/bin/bash
#copy the package $2 from branch $1 into src with name _$1
mkdir tmp 2>/dev/null
currentBranch=$(git symbolic-ref --short HEAD)
git checkout $1 2>/dev/null
teamA=$(echo _$1 | sed "s/-//g")
./copypackage.sh $2 $teamA
cp -r src/$teamA tmp
rm -r src/$teamA
git checkout $currentBranch 2>/dev/null >/dev/null 
cp -r tmp/$teamA src