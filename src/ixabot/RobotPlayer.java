package ixabot;
import battlecode.common.*;

public strictfp class RobotPlayer {
	public static void run(RobotController rc) throws GameActionException {
	        switch(rc.getType()) {
        		case MINER: new Miner(rc).run();
        		case SOLDIER: new Soldier(rc).run();
        		case ARCHON: new Archon(rc).run();
        		default: break;
        	}
    	}
}
