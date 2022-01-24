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
        int income = rc.readSharedArray(INDEX_INCOME) / 80;
        int numLabs = rc.readSharedArray(INDEX_LAB);
        if (rc.getMode() == RobotMode.PROTOTYPE) return;

        // convert lead to gold if the conversion rate is better than 5:1
        if (rc.getTransmutationRate() <= 5 && (income > numLabs * rc.getTransmutationRate() || rc.getTeamLeadAmount(rc.getTeam()) > 55)) {
            if (rc.canTransmute())
                rc.transmute();
            rc.setIndicatorString("transmuting, income = " + income);
        } else {
            rc.setIndicatorString("not transmuting, income = " + income);
        }


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
