package ixabot;
import battlecode.common.*;
import java.util.Random;

public abstract class Robot {
	final RobotController rc;
        MapLocation home;

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
                home = rc.getLocation();
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
		for (int r = 0; r <= rc.getType().visionRadiusSquared; r++){
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
                return (l.x<<6) | l.y ;
        }
        public static final MapLocation intToLoc(int x) {
                return new MapLocation(x>>6, x&0x7f);
        }
	public Direction directionTo(MapLocation l) throws GameActionException{
		MapLocation me = rc.getLocation();
		return me.directionTo(l);
	}

        public void saveValue(int index, int value, int size) throws GameActionException{
                int arrayLocation = index >> 4;
                int innerLocation = index & 0xf;
                int storeValue = value;
                int newSize = size;
                if (size > 16-innerLocation){
                        int iSize = size - (16 - innerLocation);
                        saveValue(index+size-iSize, ((1 << iSize)-1) & value, iSize);
                        storeValue >>= iSize;
                        newSize = size-iSize;
                }
                int currentValue = rc.readSharedArray(arrayLocation);
                int andValue = (((1 << innerLocation)-1) << (16-innerLocation)) + (1 << (16-innerLocation-newSize)) - 1;
                int orValue = storeValue << (16-innerLocation-newSize);
                int saveValue = (currentValue&andValue) + orValue;
                rc.setIndicatorString(Integer.toString(saveValue));                
                //try{
			rc.writeSharedArray(arrayLocation, saveValue);
		//}
		//catch(Exception e){
		//	int i = 10;
		//}
        }

        public int readValue(int index, int size) throws GameActionException{
                int arrayLocation = index >> 4;
                int innerLocation = index & 0xf;
                int newSize = size;
                int arrayValue = rc.readSharedArray(arrayLocation);
                int value = 0;
                if(size > 16-innerLocation){
                        int iSize = size - (16-innerLocation);
                        newSize = size-iSize;
                        value = (arrayValue >> (16-innerLocation-newSize)) % (int)Math.pow(2, newSize);
                        value <<= iSize;
                        value += readValue((arrayLocation+1)*16,iSize);
                }
                else{
                        value = (arrayValue >> (16-innerLocation-newSize)) % (int)Math.pow(2, newSize);
                }
                return value;
        }

        public void move(Direction d) throws GameActionException{
                MapLocation myLocation = rc.getLocation();
                Direction leftDirection = d.rotateLeft();
                Direction rightDirection = d.rotateRight();
                MapLocation forwardSquare = myLocation.add(d);
                MapLocation leftSquare = myLocation.add(leftDirection);
                MapLocation rightSquare = myLocation.add(rightDirection);
                int forwardRubble = rc.senseRubble(forwardSquare);
                int leftRubble = rc.senseRubble(leftSquare);
                int rightRubble = rc.senseRubble(rightSquare);
                if(rc.senseRobotAtLocation(forwardSquare) != null){
                        forwardRubble = 101;
                }
                if(rc.senseRobotAtLocation(leftSquare) != null){
                        leftRubble = 101;
                }
                if(rc.senseRobotAtLocation(rightSquare) != null){
                        rightRubble = 101;
                }
                if(forwardRubble >= leftRubble){
                        if(forwardRubble >= rightRubble){
                                rc.move(d);
                        }
                        else{
                                rc.move(rightDirection);
                        }
                }
                else{
                        if(leftRubble>=rightRubble){
                                rc.move(leftDirection);
                        }
                        else{
                                rc.move(rightDirection);
                        }
                }
        }
        public MapLocation getPossibleEnemyHQLocation() throws GameActionException{
		int myHQ = rng.nextInt(readValue(0,1));
                MapLocation myHQLocation = intToLoc(readValue(myHQ*12, 12));
                int x = myHQLocation.x;
                int y = myHQLocation.y;
                int nx = rc.getMapWidth() - x;
                int ny = rc.getMapHeight() - y;
                switch (rng.nextInt(5)){
                        case 0: return new MapLocation(x, ny);
                        case 1: return new MapLocation(nx, y);
                        case 2: return new MapLocation(nx, ny);
                        case 3: return new MapLocation(ny, x);
                        case 4: return new MapLocation(y, nx);
                }
                return new MapLocation(x, y);
        }
        public MapLocation getRandomEnemyHQLocation() throws GameActionException{
                int numKnown = readValue(49, 1);
                if(numKnown==0){
                        return getPossibleEnemyHQLocation();
                }
                return intToLoc(readValue(50+(rng.nextInt(numKnown)*12), 12));
        }
        public void checkEnemyHQLocation() throws GameActionException{
                RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
                int numKnownEnemyHQs = readValue(49,1);
                boolean foundNewHQ = false;
                MapLocation[] locations = {null, null, null, null};
                for(int i = 0; i<numKnownEnemyHQs; i++){
                        locations[i] = intToLoc(readValue(50+i*12, 12));
                }
                for(int i = 0; i<nearbyRobots.length; i++){
                        RobotInfo robot = nearbyRobots[i];
                        if(robot.type == RobotType.ARCHON && robot.team == rc.getTeam()){
                                boolean newArchon = true;
                                for(int j = 0;j<4;j++){
                                        if(robot.location.equals(locations[i])){
                                                newArchon = false;                         
                                        }
                                }
                                if(newArchon){
                                        saveValue(50+numKnownEnemyHQs*12, locToInt(robot.location), 12);
                                        numKnownEnemyHQs++;
                                        foundNewHQ = true;
                                }
                        }
                }
                if(foundNewHQ){
                        saveValue(49, numKnownEnemyHQs, 1);
                }
        }

        public void moveRandomly(){
                Direction dir = directions[rng.nextInt(directions.length)];
        }

}
