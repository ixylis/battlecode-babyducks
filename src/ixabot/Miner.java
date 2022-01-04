package ixabot;

import battlecode.common.*;

class Miner extends Droid {
	final int actionCooldown = 2;
	final int moveCooldown = 20;
	final int visionRadius = 20;
	final int actionRadius = 2;

	Miner(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		mine();
		moveTowardsDeposit();
	}

	public void mine() throws GameActionException{
		MapLocation me = rc.getLocation();
		for (int dx = -1*actionRadius; dx <= actionRadius; dx++) {
			for (int dy = -1*actionRadius; dy <= actionRadius; dy++) {
				MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
				while (rc.canMineLead(mineLocation)) {
					rc.mineLead(mineLocation);
				}
			}
        	}
	}
	public void moveTowardsDeposit() throws GameActionException{
		MapLocation deposit = findNearestDeposit();
		Direction depositDirection = directionTo(deposit);
		try{
			rc.move(depositDirection);
		}
		catch(Exception e){
			moveRandomly();
		}
	}

}
