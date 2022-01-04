package ixabot;

import battlecode.common.*;
import java.util.Random;

class Archon extends Building {
	final int actionCooldown = 10;
	final int moveCooldown = 24;
	final int actionRadius = 20;
	final int visionRadius = 34;

	Archon(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		spawnRandom();	
	}

	public void spawnRandom() throws GameActionException {
	Direction dir = directions[rng.nextInt(directions.length)];
		if (rng.nextBoolean()) {
		    	if (rc.canBuildRobot(RobotType.MINER, dir)) {
				rc.buildRobot(RobotType.MINER, dir);
		    	}
		} else {
		    	if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
				rc.buildRobot(RobotType.SOLDIER, dir);
		    	}
		}
	}
}
