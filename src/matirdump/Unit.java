package matirdump;

import battlecode.common.*;

import java.util.*;
import static java.lang.Math.*;

public abstract strictfp class Unit {

    RobotController rc;
    Team US, THEM;
    int mapWidth, mapHeight;

    ArrayList<MapLocation> corners;
    ArrayList<Resource> resources;
    ArrayList<MapLocation> potentials;
    ArrayList<MapLocation> ourArchons;
    ArrayList<MapLocation> enemyArchons;
    ArrayList<MapLocation> enemies;
    int symmetry;

    AnomalyScheduleEntry[] anomalies;
    int anomalyIndex;

    static final Random rng = new Random(259);
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    MapLocation myloc;

    class Resource {
        MapLocation loc;
        int amount;

        Resource(MapLocation loc, int amount) {
            this.loc = loc;
            this.amount = amount;
        }

        double value()
        {
            return this.amount / (1 + sqrt(myloc.distanceSquaredTo(this.loc)));
        }
    }

    class DirComp implements Comparator<Direction> {

        double theta;

        DirComp(double theta) {
            this.theta = theta;
        }

        double phi(Direction d) {
            return atan2(d.getDeltaY(), d.getDeltaX());
        }

        @Override
        public int compare(Direction d1, Direction d2) {
            return Double.compare(cos(theta-phi(d2)), cos(theta-phi(d1)));
        }
    }

    class DistComp implements Comparator<MapLocation> {

        @Override
        public int compare(MapLocation l1, MapLocation l2) {
            return Integer.compare(myloc.distanceSquaredTo(l1), myloc.distanceSquaredTo(l2));
        }
    }

    DistComp distComp = new DistComp();

    Unit(RobotController rc) throws GameActionException {
        this.rc = rc;

        this.US = rc.getTeam();
        this.THEM = US.opponent();

        this.mapWidth = rc.getMapWidth();
        this.mapHeight = rc.getMapHeight();
        corners.add(new MapLocation(0, 0));
        corners.add(new MapLocation(mapWidth-1, 0));
        corners.add(new MapLocation(0, mapHeight-1));
        corners.add(new MapLocation(mapWidth-1, mapHeight-1));

        this.anomalies = rc.getAnomalySchedule();

        this.myloc = rc.getLocation();
        this.init();
        this.step();
        Clock.yield();
        getSharedLocs();

        for(MapLocation archon : ourArchons)
        {
            potentials.add(new MapLocation(mapWidth - 1 - archon.x, archon.y));
            potentials.add(new MapLocation(archon.x, mapHeight - 1 - archon.y));
            potentials.add(new MapLocation(mapWidth - 1 - archon.x, mapHeight - 1 - archon.y));
        }
    }

    void run() throws GameActionException {
        while(true) {
            this.myloc = rc.getLocation();

            this.step();

            Clock.yield();
        }
    }

    abstract void init() throws GameActionException;
    abstract void step() throws GameActionException;

    AnomalyScheduleEntry getNextAnomaly() {

        while(anomalyIndex < anomalies.length)
        {
            if(anomalies[anomalyIndex].roundNumber > rc.getRoundNum())
                return anomalies[anomalyIndex];

            anomalyIndex++;
        }

        return null;
    }

    ArrayList<MapLocation> getLocsInRange(int radsq) throws GameActionException {
        int rad = (int) floor(sqrt(radsq));
        ArrayList<MapLocation> locsInRange = new ArrayList<MapLocation>();

        for(int dx = -rad; dx <= rad; dx++) {
            for(int dy = -rad; dy <= rad; dy++) {
                MapLocation newLoc = myloc.translate(dx, dy);

                if(rc.onTheMap(newLoc) && myloc.isWithinDistanceSquared(newLoc, radsq)) {
                    locsInRange.add(newLoc);
                }
            }
        }

        return locsInRange;
    }
//
//    MapLocation getFarthestCorner() {
//        return getFarthestCorner(myloc);
//    }
//
//    MapLocation getFarthestCorner(MapLocation loc)
//    {
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
//        return bc;
//    }

    void getSharedLocs() throws GameActionException {
        int[] val = new int[12];

        for(int i = 0; i < 12; ++i)
            val[i] = rc.readSharedArray(i);

        ArrayList<MapLocation> rpos = new ArrayList<>();
        extract4(val[0], val[1], val[2], ourArchons);

        extract4(val[3], val[4], val[5], enemies);
        extract4(val[6], val[7], val[8], rpos);
        resources.add(new Resource(rpos.get(0), val[9] & 0xFF00));
        resources.add(new Resource(rpos.get(1), (val[9] & 0xFF00) >> 8));
        resources.add(new Resource(rpos.get(2), val[10] & 0xFF00));
        resources.add(new Resource(rpos.get(3), (val[10] & 0xFF00) >> 8));

        if(val[11] == rc.getArchonCount()) {

        }
    }

    void extract4(int v1, int v2, int v3, ArrayList<MapLocation> locs) {
        locs.add(new MapLocation(v1 & 0x3F, (v1 & 0xFC0) >> 6));
        locs.add(new MapLocation((v1 & 0xF000) >> 12 + (v2 & 0x003) << 4, (v2 & 0xFC) >> 2));
        locs.add(new MapLocation((v2 & 0x3F00) >> 8, (v2 & 0xC000) >> 10 + (v3 & 0xF) << 2));
        locs.add(new MapLocation((v3 & 0x3F0) >> 4, (v3 & 0xFC00) >> 10));
    }

    void writeHext(int k, int val) throws GameActionException {
        int q = (k*6)/16, r = (k*6) % 16;
        if (r <= 10) rc.writeSharedArray(q, rc.readSharedArray(q) | (val << r));
        else {
            rc.writeSharedArray(q, rc.readSharedArray(q) | ((val & ((1<<(16-r))-1))<<r));
            rc.writeSharedArray(q+1, rc.readSharedArray(q+1) |
                    ((val & (((1<<(r-10))-1) << (16-r))) >> (16-r)));
        }
    }
}