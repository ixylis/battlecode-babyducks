package matir;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class SharedState {
    // TODO: Implement sharing
    Unit unit;

    SharedState(Unit unit)
    {
        this.unit = unit;
    }

    void shareInfo() {

    }

    MapLocation getTarget() {
        switch (unit.rc.getType()) {
            case SOLDIER: return unit.getFarthestCorner();
            default: return null;
        }
    }
}
