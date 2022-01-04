package ixabot;

import battlecode.common.*;

class Soldier extends Droid {
	final int actionCooldown = 10;
	final int moveCooldown = 16;
	final int visionRadius = 20;
	final int actionRadius = 13;
        MapLocation targetLocation = null;

	Soldier(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		attackSomething();
                int sharedTarget = rc.readSharedArray(0);
                MapLocation sharedTargetLocation = intToLoc(sharedTarget);
                RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
                if(sharedTargetLocation.equals(targetLocation)){
                        if (enemies.length == 0){
                                rc.writeSharedArray(0, 0);
                        }
                }
                if (targetLocation != null){
                        if(sharedTarget == 0){
                                rc.writeSharedArray(0, locToInt(targetLocation));
                        }
                        Direction targetDirection = directionTo(targetLocation);
                        try{
                                rc.move(targetDirection);
                        }
                        catch(Exception e){
                                moveRandomly();
                        }
                }
                else{
                        if(enemies.length > 0){
                                targetLocation = enemies[0].location;
                        }
                        else{
                                targetLocation = null;
                                if (sharedTarget != 0){
                                        targetLocation = sharedTargetLocation;
                                }
                                moveRandomly();
                        }
                }
	}

	public void attackSomething() throws GameActionException{
		int radius = rc.getType().actionRadiusSquared;
		Team opponent = rc.getTeam().opponent();
		RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
		if (enemies.length > 0) {
		    MapLocation toAttack = enemies[0].location;
		    if (rc.canAttack(toAttack)) {
			rc.attack(toAttack);
		    }
		}
	}

}
