package matir;

import battlecode.common.*;

import java.util.*;
import static java.lang.Math.*;
import static battlecode.common.Direction.*;

public abstract strictfp class Droid extends Unit {

    MapLocation targetLoc;

    class TargetWeight extends Weightage {

        @Override
        double weight(Direction d) {
            MapLocation newLoc = myLoc.add(d);
            double curDist = myLoc.distanceSquaredTo(targetLoc);
            double newDist = newLoc.distanceSquaredTo(targetLoc);
            if(newDist > curDist) return 1;
            return (sqrt(curDist) - sqrt(newDist) + 1) * (1 + rubbleWeight.weight(d));
        }
    }
    TargetWeight targetWeight = new TargetWeight();

    Droid(RobotController rc) throws GameActionException {
        super(rc);
        targetLoc = renewTarget();
    }

    boolean move() throws GameActionException {
        ArrayList<Direction> dirs = new ArrayList<Direction>();

        for(Direction d : directions) {
            if(rc.canMove(d)) {
                dirs.add(d);
            }
        }

        if(dirs.isEmpty()) return false;

        if(targetLoc == null) {

            rc.move(randDirByWeight(dirs));

            return true;

        } else {

            rc.move(randDirByWeight(dirs, targetWeight));

            return true;
        }
    }

    abstract MapLocation renewTarget() throws GameActionException;
}
