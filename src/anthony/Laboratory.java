package anthony;

import battlecode.common.*;

import static java.lang.Math.*;

public class Laboratory extends Robot {
    int lastActive;
    MapLocation movementTarget, highground = new MapLocation(0, 0);
    double highgroundRubble = 10000;

    Laboratory(RobotController r) throws GameActionException {
        super(r);
        lastActive = rc.getRoundNum();
    }

    public void turn() throws GameActionException {
        if (rc.getMode() == RobotMode.PROTOTYPE) return;

        // convert lead to gold if the conversion rate is better than 5:1
        if (rc.getTransmutationRate() <= 5 && rc.canTransmute() && rc.getTeamLeadAmount(rc.getTeam()) > 55)
            rc.transmute();


        rc.setIndicatorDot(highground,0,0,255);

        if (rc.getMode() == RobotMode.PORTABLE) {
            if ((movementTarget != null &&
                    myLoc.isWithinDistanceSquared(movementTarget, 50))) {
                if ((highground == myLoc || rc.canSenseRobotAtLocation(highground))
                        && rc.canTransform()) {
                    rc.transform();
                } else {
                    moveToward(highground);
                }
                lastActive = rc.getRoundNum();
            } else {
                moveToward(movementTarget);
            }
        } else {
            // see if you want to move to low rubble tile
        }
    }
}
