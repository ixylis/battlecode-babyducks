package ixabot;

import battlecode.common.*;
import java.util.Random;

class Archon extends Building {
	final int actionCooldown = 10;
        int miners = 0;
        int prevTurnMoney = 0;
        int soldiers = 0;

	Archon(RobotController rc)throws GameActionException{
		super(rc);
                int numHQLocations = readValue(0,1);
                saveValue(1+numHQLocations*12, locToInt(rc.getLocation()), 12);
                saveValue(0, numHQLocations+1, 1);
	}
	
	public void turn() throws GameActionException{
		spawn();
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

        public void spawn() throws GameActionException {
	        Direction dir = directions[rng.nextInt(directions.length)];
                int income = rc.getTeamLeadAmount(rc.getTeam()) - prevTurnMoney;
                //rc.setIndicatorString(Integer.toString(miners));
                if(income > miners * 2 || miners < 3){
                        if(rc.canBuildRobot(RobotType.MINER, dir)){
                                rc.buildRobot(RobotType.MINER, dir);
                                miners++;
                        }
                } else {
                        if(rc.canBuildRobot(RobotType.SOLDIER, dir)){
                                rc.buildRobot(RobotType.SOLDIER, dir);
                                soldiers++;
                        }
                }
                prevTurnMoney = rc.getTeamLeadAmount(rc.getTeam());
        }

}
