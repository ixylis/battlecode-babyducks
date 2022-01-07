package ixabot;

import battlecode.common.*;
import java.util.Random;

class Archon extends Building {
	final int actionCooldown = 10;
	final int moveCooldown = 24;
	final int actionRadius = 20;
	final int visionRadius = 34;
        int miners = 0;
        int prevTurnMoney = 0;
        int soldiers = 0;

	Archon(RobotController rc){
		super(rc);
	}
	
	public void turn() throws GameActionException{
		spawn();
                saveValue(6,5,4);
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
                rc.setIndicatorString(Integer.toString(miners));
                if(income > miners * 2 || miners < 3){
                        try{
                                rc.buildRobot(RobotType.MINER, dir);
                                miners++;
                        } catch(Exception e){
                        }
                } else {
                        try{
                                rc.buildRobot(RobotType.SOLDIER, dir);
                                soldiers++;
                        } catch (Exception e){
                        }
                }
                prevTurnMoney = rc.getTeamLeadAmount(rc.getTeam());
        }

        public void calculatePossibleEnemyHQ(){
                MapLocation[] hqLocations = {};
                MapLocation myLocation = rc.getLocation();
                int x = myLocation.x;
                int y = myLocation.y;
                int nx = rc.getMapWidth() - myLocation.x;
                int ny = rc.getMapHeight() - myLocation.y;
                //hqLocations.add(new MapLocation(x, ny));
                //hqLocations.add(new MapLocation(nx, y));
                //hqLocations.add(new MapLocation(nx, ny));
                //hqLocations.add(new MapLocation(ny, x));
                //hqLocations.add(new MapLocation(y, nx));
                //return hqLocations;
        }

        public void savePossibleEnemyHQ(){
                int mapSize = 10;
        }
}
