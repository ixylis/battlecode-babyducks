package ixabot;
import battlecode.common.*;
import java.util.Random;

public abstract class Robot {
	final RobotController rc;
	int actionCooldown;
	int moveCooldown;
	int visionRadius;
	int actionRadius;

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

	public MapLocation findNearestDeposit() throws GameActionException {
		MapLocation me = rc.getLocation();
		for (int r = 0; r <= visionRadius; r++){
			for (int dx = 0; dx <= r; dx++){
				int dy = r;
				MapLocation mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 0){
					return mineLocation;
				}
				dy = -r;
				mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 0){
					return mineLocation;
				}
			}
			for (int dy = 0; dy <= r; dy++){
				int dx = r;
				MapLocation mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 0){
					return mineLocation;
				}
				dx = -r;
				mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 0){
					return mineLocation;
				}
			}
		}
		return null;
	}
        public static final int locToInt(MapLocation l) {
                return (l.x<<7) | l.y | 0x4000;
        }
        public static final MapLocation intToLoc(int x) {
                return new MapLocation((x>>7)&0x7f, x&0x7f);
        }
	public Direction directionTo(MapLocation l) throws GameActionException{
		MapLocation me = rc.getLocation();
		return me.directionTo(l);
	}


}
