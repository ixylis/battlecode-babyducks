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
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 1){
					return mineLocation;
				}
				dy = -r;
				mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 1){
					return mineLocation;
				}
			}
			for (int dy = 0; dy <= r; dy++){
				int dx = r;
				MapLocation mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 1){
					return mineLocation;
				}
				dx = -r;
				mineLocation = new MapLocation(me.x+dx, me.y+dy);
				if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 1){
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

        public void saveValue(int index, int value, int size) throws GameActionException{
                int arrayLocation = index / 16;
                int innerLocation = index % 16;
                int storeValue = value;
                int newSize = size;
                if (size > 16-innerLocation){
                        int iSize = size - (16 - location);
                        saveValue((arrayLocation+1)*16, (1 << iSize)-1, iSize);
                        storeValue >>= iSize;
                        newSize = size-iSize;
                }
                int currentValue = rc.readSharedArray(arrayLocation);
                int andValue = (((1 << innerLocation)-1) << (16-innerLocation)) + (1 << (16-innerLocation-size)) - 1;
                int orValue = storeValue << 16-innerLocation-newSize;
                int saveValue = (currentValue&andValue) | orValue;
                
                rc.writeSharedArray(arrayLocation, saveValue);
        }

        public int readValue(int index, int size) throws GameActionException{
                int arrayLocation = index / 16;
                int innerLocation = index % 16;
                int newSize = size;
                int arrayValue = rc.readSharedArray(arrayLocation);
                int value = 0
                if(size > 16-innerLocation){
                        int iSize = size - (16-location);
                        newSize = size-iSize;
                        value = (arrayValue >> (16-innerLocation-newSize)) % (int)Math.pow(2, newSize);
                        value <<= iSize
                        value += readValue((arrayLocation+1)*16,iSize);
                }
                else{
                        value = (arrayValue >> (16-innerLocation-newSize)) % (int)Math.pow(2, newSize);
                }
                return value;
        }

        public void move(Direction d) throws GameActionException{
                MapLocation myLocation = rc.getLocation();
                int forward = rc.senseRubble(myLocation.add(d));
                int left = rc.senseRubble(myLocation.add(d.rotateLeft()));
                int right = rc.senseRubble(myLocation.add(d.rotateRight()));
                if(rc.senseRobotAtLocation(d) != null){
                        forward = 101
                }
                if(rc.senseRobotAtLocation(d.rotateLeft()) != null){
                        left = 101
                }
                if(rc.senseRobotAtLocation(d.rotateRight()) != null){
                        right = 101
                }
                if(forward >= left){
                        if(forward >= right){
                                rc.move(d);
                        }
                        else{
                                rc.move(d.rotateRight());
                        }
                }
                else{
                        if(left>=right){
                                rc.move(d.rotateLeft());
                        }
                        else{
                                rc.move(d.rotateRight());
                        }
                }
        }


}
