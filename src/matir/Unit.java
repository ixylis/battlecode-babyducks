package matir;

import static java.lang.Math.*;
import battlecode.common.*;
import java.util.*;

public abstract strictfp class Unit {

    RobotController rc;
    Team US, THEM;
    int mapWidth, mapHeight;
    ArrayList<MapLocation> corners;

    static final Random rng = new Random();
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
            Direction.CENTER
    };

    MapLocation myLoc;

    SharedState sharedState;

    abstract class Weightage {abstract double weight(Direction d);}

    class RubbleWeight extends Weightage {
        double weight(Direction d) {
            try {
                return 1.0 / (1 + rc.senseRubble(myLoc.add(d)));
            } catch(GameActionException e) {
                e.printStackTrace();
            }

            return 0.0;
        }
    }
    RubbleWeight rubbleWeight = new RubbleWeight();

    Unit(RobotController rc) {
        this.rc = rc;

        US = rc.getTeam();
        THEM = US.opponent();

        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();

        corners = new ArrayList<MapLocation>();
        corners.add(new MapLocation(0, 0));
        corners.add(new MapLocation(mapWidth - 1, 0));
        corners.add(new MapLocation(0, mapHeight - 1));
        corners.add(new MapLocation(mapWidth - 1, mapHeight - 1));

        sharedState = new SharedState(this);
    }

    void run() {
        while(true) {
            this.myLoc = rc.getLocation();

            try {
                this.step();
            } catch (GameActionException e) {
                e.printStackTrace();
            }

            sharedState.shareInfo();

            Clock.yield();
        }
    }

    abstract void step() throws GameActionException;

//    ArrayList<MapLocation> getLocsInRange(int radsq) throws GameActionException {
//        int rad = (int) floor(sqrt(radsq));
//        ArrayList<MapLocation> locsInRange = new ArrayList<MapLocation>();
//
//        for(int dx = -rad; dx <= rad; dx++) {
//            for(int dy = -rad; dy <= rad; dy++) {
//                MapLocation newLoc = myLoc.translate(dx, dy);
//
//                if(rc.onTheMap(newLoc) && myLoc.isWithinDistanceSquared(newLoc, radsq)) {
//                    locsInRange.add(newLoc);
//                }
//            }
//        }
//
//        return locsInRange;
//    }

    MapLocation getFarthestCorner() {
        return getFarthestCorner(myLoc);
    }

    MapLocation getFarthestCorner(MapLocation loc)
    {
        return corners.get(rng.nextInt(4));

//        int bd = 10000;
//        MapLocation bc = null;
//
//        for (MapLocation corner : corners) {
//            int d = loc.distanceSquaredTo(corner);
//            if(d < bd)
//            {
//                bd = d;
//                bc = corner;
//            }
//        }
//
//        sharedState = new SharedState(rc);
//
//        return bc;
    }

    Direction randDirByWeight(ArrayList<Direction> dirs) {
        return randDirByWeight(dirs, rubbleWeight);
    }

    Direction randDirByWeight(ArrayList<Direction> dirs, Weightage wt) {

        double[] cwt = new double[dirs.size()+1];
        cwt[0] = 0;

        for(int i=1;i<=dirs.size();++i)
        {
            cwt[i] = cwt[i-1] + wt.weight(dirs.get(i-1));
        }

        if(cwt[dirs.size()] == 0) return null;

        for(int i=1;i<=dirs.size();++i)
        {
            cwt[i] /= cwt[dirs.size()];
        }

        double r = rng.nextDouble();
        int i = 0;
        while(cwt[i] < r) i++;

        return dirs.get(i-1);
    }

}