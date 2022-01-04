package ixabot;

import battlecode.common.*;

class Soldier extends Droid {
	final int actionCooldown = 10;
	final int moveCooldown = 16;
	final int visionRadius = 20;
	final int actionRadius = 13;

	Soldier(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		attackSomething();
		moveRandomly();
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
