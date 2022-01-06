package matir;

import battlecode.common.*;
import static battlecode.common.RobotType.*;
import java.util.*;

public strictfp class Miner extends Droid {

    public Miner(RobotController rc) throws GameActionException {
        super(rc);
    }

    class LeadComp implements Comparator<MapLocation> {

        @Override
        public int compare(MapLocation l1, MapLocation l2) {
            try {
                return -Integer.compare(rc.senseLead(l1), rc.senseLead(l2));
            } catch (GameActionException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }
    LeadComp leadComp = new LeadComp();

//    class UnclaimedLeadComp implements Comparator<MapLocation> {
//
//        HashMap<MapLocation, Integer> miners;
//
//        UnclaimedLeadComp() {
//            miners = new HashMap<MapLocation, Integer>();
//            RobotInfo[] friends = rc.senseNearbyRobots(MINER.visionRadiusSquared, US);
//
//            for(RobotInfo friend : friends) {
//                if(friend.getType() != MINER) continue;
//
//                for(Direction d : directions) {
//                    MapLocation newLoc = friend.location.add(d);
//                    miners.putIfAbsent(newLoc, 0);
//                    miners.put(newLoc, miners.get(newLoc) + 1);
//                }
//            }
//        }
//
//        @Override
//        public int compare(MapLocation l1, MapLocation l2) {
//            return Integer.compare(value(l1), value(l2));
//        }
//
//        int value(MapLocation loc) {
//            miners.putIfAbsent(loc, 0);
//            int minerCount = miners.get(loc);
//            if(minerCount >= 8) return 0;
//            try {
//                return rc.senseLead(loc) / (1 + miners.get(loc) ^ 2);
//            }
//            catch (GameActionException e) {
//                e.printStackTrace();
//            }
//
//            return 0;
//        }
//    }

    @Override
    void step() throws GameActionException {
        if(targetLoc != null) {
            if(rc.canSenseLocation(targetLoc)) {
                targetLoc = renewTarget();
            }
        }

        move();

        MapLocation[] locs = rc.senseNearbyLocationsWithLead(MINER.actionRadiusSquared);
        Collections.sort(Arrays.asList(locs), leadComp);

        int threshold = 1;

        if(rc.senseNearbyRobots(MINER.visionRadiusSquared, THEM).length > 0) threshold = 0;

        for(MapLocation loc : locs) {
            while(rc.canMineLead(loc) && rc.senseLead(loc) > threshold) {
                rc.mineLead(loc);
            }

            if(!rc.isActionReady()) return;
        }
    }

    @Override
    MapLocation renewTarget() throws GameActionException {

        MapLocation[] freshLocs = rc.senseNearbyLocationsWithLead(MINER.visionRadiusSquared);

        if(freshLocs.length == 0) {
            return sharedState.getTarget();
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(MINER.visionRadiusSquared, THEM);
        RobotInfo[] friends = rc.senseNearbyRobots(MINER.actionRadiusSquared, US);

        int miners = 0;
        for(RobotInfo friend : friends) {
            if(friend.getType() == MINER) miners++;
        }

        if(enemies.length > 0 || miners < 3) {
            return Collections.min(Arrays.asList(freshLocs), leadComp); // leadComp gives inverse results
        } else {
            return null;
        }
    }
}
