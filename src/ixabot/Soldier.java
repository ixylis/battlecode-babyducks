package ixabot;

import battlecode.common.*;

class Soldier extends Droid {
	Soldier(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		moveRandomly();
	}

}
