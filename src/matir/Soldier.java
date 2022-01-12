package matir;

import battlecode.common.*;
import josh.Robot;

import static battlecode.common.GameConstants.*;
import static battlecode.common.RobotType.*;
import static java.lang.Math.*;
import java.util.*;

public class Soldier extends Droid {

    boolean retreat = false;

    public Soldier(RobotController rc) throws GameActionException {
        super(rc);
    }

    class HealthComp implements Comparator<RobotInfo> {
        @Override
        public int compare(RobotInfo r1, RobotInfo r2) {
            if(r1.getType() == SOLDIER && r2.getType() != SOLDIER) return -1;
            if(r1.getType() != SOLDIER && r2.getType() == SOLDIER) return  1;
            return Integer.compare(r1.getHealth(), r2.getHealth());
        }
    }
    HealthComp healthComp = new HealthComp();

    @Override
    void step() throws GameActionException {
        RobotInfo[] enemyUnits = rc.senseNearbyRobots(SOLDIER.visionRadiusSquared, THEM);

        if(enemyUnits.length > 0 || rc.canSenseLocation(targetLoc) || retreat) {
            targetLoc = renewTarget();
        }

        if(shoot()) {
            move();
        } else {
            move();
            shoot();
        }
    }

    boolean shoot() throws GameActionException {
        if(!rc.isActionReady()) return false;
        RobotInfo[] shootables = rc.senseNearbyRobots(SOLDIER.actionRadiusSquared, THEM);

        if(shootables.length == 0) return false;

        MapLocation shootLoc = (Collections.min(Arrays.asList(shootables), healthComp).getLocation());
        if(rc.canAttack(shootLoc)) rc.attack(shootLoc);
        return true;
    }

    @Override
    MapLocation renewTarget() {
        RobotInfo[] alliedUnits = rc.senseNearbyRobots(SOLDIER.visionRadiusSquared, US);
        RobotInfo[] enemyUnits  = rc.senseNearbyRobots(SOLDIER.visionRadiusSquared, THEM);

        int enemyHP = 0, friendlyHP = 0;

        for(RobotInfo rb : enemyUnits) {
            if(rb.getType() == SOLDIER) {
                enemyHP += rb.getHealth();
            }
        }

        for(RobotInfo rb : alliedUnits) {
            if(rb.getType() == SOLDIER || rb.getType() == ARCHON) {
                friendlyHP += rb.getHealth();
            }
        }

        if(friendlyHP >= enemyHP) {
            // charge!
            retreat = false;

            if(enemyUnits.length > 0)
                return Collections.min(Arrays.asList(enemyUnits), healthComp).getLocation();
            else
                return null;
        }

        // strategic retreat time
        double tx = 0, ty = 0;

        for(RobotInfo rb : enemyUnits)
        {
            if(rb.getType() != SOLDIER) continue;
            MapLocation loc = rb.getLocation();
            int dx = loc.x - myLoc.x, dy = loc.y - myLoc.y;
            if(dx == 0 && dy == 0) continue;
            double dxh = dx/sqrt(dx^2+dy^2), dyh = dy/sqrt(dx^2+dy^2);
            tx += dxh; ty += dyh;
        }

        retreat = true;
        return myLoc.translate(-(int)tx, -(int)ty);
    }

}
