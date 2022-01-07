package josh;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class Soldier extends Robot {
    Soldier(RobotController r) throws GameActionException {
        super(r);
    }
    private MapLocation movementTarget = null;
    public void turn() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        boolean existsSoldier=false;
        int enemySoldierCount=0;
        int friendlySoldierCount=0;
        MapLocation away=rc.getLocation();
        for(RobotInfo r : rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam())) {
            if(r.type == RobotType.SOLDIER)
                friendlySoldierCount++;
        }
        if(enemies.length>0) {
            for(RobotInfo r : enemies) {
                if(r.type == RobotType.SOLDIER) {
                    //find the lowest rubble tile you can move onto.
                    existsSoldier = true;
                    enemySoldierCount++;
                    away=away.translate(rc.getLocation().x-r.location.x, rc.getLocation().y-r.location.y);
                }
            }
            if(enemySoldierCount>friendlySoldierCount)
                moveToward(away);
            if(existsSoldier && rc.isMovementReady()) {
                int minRubble = rc.senseRubble(rc.getLocation());
                Direction minRubbleDir = Direction.CENTER;
                for(Direction d : Robot.directions) {
                    int rubble = rc.senseRubble(rc.getLocation().add(d));
                    if(rubble < minRubble && rc.canMove(d)) {
                        minRubble = rubble;
                        minRubbleDir = d;
                    }
                }
                if(minRubbleDir != Direction.CENTER)
                    rc.move(minRubbleDir);
            }
            if(!existsSoldier && rc.isMovementReady()) moveToward(enemies[0].location);
        } else {
            if(movementTarget!=null && rc.canSenseLocation(movementTarget))
                movementTarget=null;
            if(movementTarget==null)
                movementTarget = super.getRandomKnownEnemyHQ();
            if(movementTarget==null)
                movementTarget = super.getRandomPossibleEnemyHQ();
            if(movementTarget==null)
                wander();
            else
                moveToward(movementTarget);
        }
        if(rc.isActionReady()) attack();
        super.updateEnemyHQs();
        rc.setIndicatorDot(Robot.intToLoc(rc.readSharedArray(INDEX_ENEMY_HQ+rc.getRoundNum()%4)), 190, 0, 190);
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
