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
        if(rc.isMovementReady())
            movement();
        else
            super.updateEnemySoliderLocations();
        if(rc.isActionReady()) attack();
        super.updateEnemyHQs();
        //rc.setIndicatorDot(Robot.intToLoc(rc.readSharedArray(INDEX_ENEMY_HQ+rc.getRoundNum()%4)), 190, 0, 190);
        rc.setIndicatorDot(Robot.intToChunk(rc.readSharedArray(Robot.INDEX_ENEMY_LOCATION+rc.getRoundNum()%Robot.NUM_ENEMY_SOLDIER_CHUNKS)), 1, 255, 1);
        
    }
    private void movement() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        boolean existsSoldier=false;
        int enemySoldierCount=0;
        int friendlySoldierCount=0;
        int adjacentFriendlySoldierCount = 0;
        MapLocation away=rc.getLocation();
        MapLocation nearbyFriend = null;
        for(RobotInfo r : rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam())) {
            if(r.type == RobotType.SOLDIER) {
                nearbyFriend = r.location;
                friendlySoldierCount++;
                if(r.location.distanceSquaredTo(rc.getLocation())<3)
                    adjacentFriendlySoldierCount++;
            }
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
            if(enemySoldierCount>friendlySoldierCount) {
                moveToward(away);
                return;
            }
            if(enemySoldierCount+1<adjacentFriendlySoldierCount) {
                moveToward(enemies[0].location);
                return;
            }
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
                if(minRubbleDir != Direction.CENTER) {
                    rc.move(minRubbleDir);
                    return;
                }
            }
            if(!existsSoldier) {
                moveToward(enemies[0].location);
                return;
            }
        } else {
            if(movementTarget!=null && rc.canSenseLocation(movementTarget))
                movementTarget=null;
            MapLocation x = super.getNearestEnemyChunk();
            if(x!=null) movementTarget=x;
            if(movementTarget==null)
                movementTarget = super.getRandomKnownEnemyHQ();
            if(movementTarget==null)
                movementTarget = super.getRandomPossibleEnemyHQ();
            
            if(rc.getLocation().distanceSquaredTo(movementTarget)>64 || adjacentFriendlySoldierCount>0)
                moveToward(movementTarget);
            else {
                moveToward(nearbyFriend);
            }
        }
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
