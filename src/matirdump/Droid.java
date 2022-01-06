package matirdump;

import battlecode.common.*;
import static java.lang.Math.*;
import static battlecode.common.Direction.*;

public abstract strictfp class Droid extends Unit {

    MapLocation targetLoc;

    Droid(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    void init() throws GameActionException {
        int val = rc.readSharedArray(11);
        targetLoc = new MapLocation(val & 0x3F, (val & 0xFC0) >> 6);
    }

    boolean move() throws GameActionException {
        int xdiff = targetLoc.x - myloc.x;
        int ydiff = targetLoc.y - myloc.y;

        Direction[] valid;

        if(abs(xdiff) > abs(ydiff)) {
            if(xdiff > 0) {
                valid = new Direction[]{NORTHEAST, EAST, SOUTHEAST};
            } else {
                valid = new Direction[]{NORTHWEST, WEST, SOUTHWEST};
            }
        } else {
            if(ydiff > 0) {
                valid = new Direction[]{NORTHWEST, NORTH, NORTHEAST};
            } else {
                valid = new Direction[]{SOUTHWEST, SOUTH, SOUTHEAST};
            }
        }

        Direction bdir = CENTER;
        int brub = 10000, rub;

        rub = rc.senseRubble(myloc.add(valid[0]));
        if(rub < brub) {
            if(rc.canMove(valid[1])) {
                bdir = valid[1];
                brub = rub;
            }
        }

        rub = rc.senseRubble(myloc.add(valid[1]));
        if(rub < brub) {
            if(rc.canMove(valid[1])) {
                bdir = valid[1];
                brub = rub;
            }
        }

        rub = rc.senseRubble(myloc.add(valid[2]));
        if(rub < brub) {
            if(rc.canMove(valid[2])) {
                bdir = valid[2];
                brub = rub;
            }
        }

        if(brub < 10000) {
            rc.move(bdir);
            return true;
        } else {
            return false;
        }
    }
}
