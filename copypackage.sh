#!/bin/bash
#copies package $1 and names the new one $2.
mkdir -p src/$2
for file in $(ls src/$1)
do
    sed "s/package $1/package $2/" src/$1/$file > src/$2/$file
done

	    
