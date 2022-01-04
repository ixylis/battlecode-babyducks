package ixabot;

import battlecode.common.*;
import java.util.Random;

abstract class Droid extends Robot {
	
	Droid(RobotController rc){
		super(rc);
	}

	public void moveRandomly() throws GameActionException {
		Direction dir = directions[this.rng.nextInt(directions.length)];
        	if (rc.canMove(dir)) {
        		rc.move(dir);
		}
	}
}
