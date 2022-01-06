package matirdump;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public abstract strictfp class Building extends Unit {

    protected Building(RobotController rc) throws GameActionException {
        super(rc);
    }
}
