package ixabot;

import battlecode.common.*;

class Miner extends Droid {

	Miner(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		mine();
		moveTowardsDeposit();
                checkEnemyHQLocation();
	}

	public void mine() throws GameActionException{
		MapLocation[] deposits = rc.senseNearbyLocationsWithLead(rc.getType().visionRadiusSquared);
                for(int i = 0; i<deposits.length; i++){
                        MapLocation l = deposits[i];
                        int leadAmount = rc.senseLead(deposits[i]);
                        while(leadAmount > 1){
                                rc.mineLead(deposits[i]);
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
