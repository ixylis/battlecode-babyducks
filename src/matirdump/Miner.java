package matirdump;

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

//    @Override
//    void init() {
//
//    }

    @Override
    void step() throws GameActionException {
        move();
        ArrayList<MapLocation> locs = getLocsInRange(MINER.actionRadiusSquared);
        Collections.sort(locs, new LeadComp());
        for(MapLocation loc : locs) {
            if(rc.canMineLead(loc)) {
                while(rc.senseLead(loc) > 20) {
                    rc.mineLead(loc);
                }
            }
        }
    }
}
