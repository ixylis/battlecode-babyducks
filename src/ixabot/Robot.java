package ixabot;
import battlecode.common.*;
import java.util.Random;

public abstract class Robot {
	final RobotController rc;
	static final Direction[] directions = {
        	Direction.NORTH,
        	Direction.NORTHEAST,
        	Direction.EAST,
		Direction.SOUTHEAST,
		Direction.SOUTH,
		Direction.SOUTHWEST,
		Direction.WEST,
		Direction.NORTHWEST,
	};
	static final Random rng = new Random(6147);

	
	Robot(RobotController rc){
		this.rc = rc;
	}
	
	public void run() throws GameActionException {
		while(true){
			try{
				turn();
			} catch (GameActionException e){
				e.printStackTrace();
			} catch (Exception e){
				e.printStackTrace();
			} finally {
				Clock.yield();
			}
		}
	}

	public abstract void turn() throws GameActionException;

}
