package ixabot;

import battlecode.common.*;

class Soldier extends Droid {
        MapLocation targetLocation = null;

	Soldier(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		attackSomething();
                moveRandomly();
                checkEnemyHQLocation();
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
