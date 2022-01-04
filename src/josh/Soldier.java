package josh;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class Soldier extends Robot {
    Soldier(RobotController r) {
        super(r);
    }
    public void turn() throws GameActionException {
        if(rc.isMovementReady()) wander();
        if(rc.isActionReady()) attack();
    }
    public void attack() throws GameActionException {
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if(enemies.length == 0) return;
        RobotInfo bestTarget = enemies[0];
        for(RobotInfo r : enemies) {
            if(bestTarget.type != RobotType.SOLDIER && r.type==RobotType.SOLDIER)
                bestTarget = r;
            else if(bestTarget.health > r.health)
                bestTarget = r;
        }
        if(rc.canAttack(bestTarget.location))
            rc.attack(bestTarget.location);
    }
}
