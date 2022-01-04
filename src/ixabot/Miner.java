package ixabot;

import battlecode.common.*;

class Miner extends Droid {
	Miner(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		moveRandomly();
	}

}
