package josh;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Miner extends Robot {
    Miner(RobotController r) throws GameActionException {
        super(r);
    }
    public void turn() throws GameActionException {
        MapLocation l = rc.getLocation();
        MapLocation loc;
        if(rc.canSenseLocation(loc=l.translate(-2, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 0)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, -1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 1)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 3)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 4)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, -2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 2)) && rc.senseLead(loc)>1) {
            moveToward(loc);
        } else {
            wander();
        }

        while(rc.isActionReady() && rc.senseLead(l)>1)
            rc.mineLead(l);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 0)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, -1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, 1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 0)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, -1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, -1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 1)) && rc.senseLead(loc)>1)
            rc.mineLead(loc);

    }
}
