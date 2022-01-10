package josh;

import java.util.Random;

import battlecode.common.*;

public abstract class Robot {
    public static final int INDEX_MY_HQ=0; //4 ints for friendly HQ locations
    public static final int INDEX_ENEMY_HQ=4; //4 ints for known enemy HQ locs
    public static final int INDEX_LIVE_MINERS=8;
    public static final int INDEX_INCOME=9;
    public static final int INDEX_ENEMY_LOCATION=10;//10 ints for recent enemy soldier locations
    public static final int NUM_ENEMY_SOLDIER_CHUNKS=10;
    public static final int INDEX_HQ_SPENDING=20; //one bit for is alive, two bits for round num mod 4, remainder for total lead spent.
    
    /*
     * intention is for each enemy seen within the last 20 rounds is in here
     * but only put distinct entries if they are more than 4 tiles apart.
     * when anyone sees an enemy check if it would be a new entry. if so add it with the round number.
     */
    
    
    public static final boolean DEBUG=true;
    public final Random rng;
    RobotController rc;
    Robot(RobotController r) throws GameActionException {
        rc = r;
        rng = new Random(rc.getID());
    }
    void run() {
        while(true) {
            try {
                turn();
            } catch(GameActionException e) {
                rc.setIndicatorString(e.getStackTrace()[2].toString());
            } catch(Exception e) {
                rc.setIndicatorString(e.getStackTrace()[0].toString());
                //rc.setIndicatorString(e.toString());
            }
            Clock.yield();
        }
    }
    public abstract void turn() throws GameActionException;
    
    /** Array containing all the possible movement directions. */
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
    
    public void moveInDirection(Direction d) throws GameActionException {
        Direction[] dd = {d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight()};
        double[] suitability = {1,.5,.5,.1,.1};
        for(int i=0;i<5;i++) {
            MapLocation l = rc.getLocation().add(dd[i]);
            if(rc.onTheMap(l))
                suitability[i] /= 10 + rc.senseRubble(l);
        }
        double best = 0;
        Direction bestD = null;
        for(int i=0;i<5;i++) {
            if(suitability[i]>best && rc.canMove(dd[i])) {
                best = suitability[i];
                bestD = dd[i];
            }
        }
        if(bestD != null) {
            rc.move(bestD);
        }
    }
    public void moveToward(MapLocation l) throws GameActionException {
        if(Robot.DEBUG) {
            rc.setIndicatorLine(rc.getLocation(), l, 255, 255, 0);
            //System.out.println("Navigating toward " + l);
        }
        if(!rc.isMovementReady()) return;
        if(rc.getLocation().equals(l)) return;
        if(rc.getLocation().isAdjacentTo(l)) {
            Direction d = rc.getLocation().directionTo(l);
            if(rc.canMove(d))
                rc.move(d);
            return;
        }
        moveInDirection(rc.getLocation().directionTo(l));
    }
    private MapLocation wanderTarget=null;
    private int lastWanderProgress = 0; //the last turn which we wandered closer to our destination
    public void wander() throws GameActionException {
        while(wanderTarget==null || rc.getLocation().distanceSquaredTo(wanderTarget)<10 || lastWanderProgress+20 < rc.getRoundNum()) {
            wanderTarget = new MapLocation(rng.nextInt(rc.getMapWidth()),rng.nextInt(rc.getMapHeight()));
            lastWanderProgress = rc.getRoundNum();
        }
        MapLocation old = rc.getLocation();
        moveToward(wanderTarget);
        if(rc.getLocation().distanceSquaredTo(wanderTarget) < old.distanceSquaredTo(wanderTarget))
            lastWanderProgress = rc.getRoundNum();
    }
    public static final int chunkToInt(MapLocation l) {
        return ((l.x>>2)<<4) | (l.y>>2);
    }
    public static final MapLocation intToChunk(int x) {
        return new MapLocation((((x>>4)&0xf)<<2)+1 , ((x&0xf)<<2)+1);
    }
    public static final int locToInt(MapLocation l) {
        return (l.x<<7) | l.y | 0x4000;
    }
    public static final MapLocation intToLoc(int x) {
        return new MapLocation((x>>7)&0x7f, x&0x7f);
    }
    /*
     * using the locations of our HQs and map symmetry, get a random possible enemy HQ location weighted by distance away.
     */
    public MapLocation getRandomPossibleEnemyHQ() throws GameActionException {
        MapLocation[] possibleEnemyHQs = new MapLocation[12];
        for(int i=0;i<4 && rc.readSharedArray(i+Robot.INDEX_MY_HQ)>0;i++) {
            MapLocation l = intToLoc(rc.readSharedArray(i+Robot.INDEX_MY_HQ));
            possibleEnemyHQs[i*3] = new MapLocation(rc.getMapWidth()-l.x,l.y);
            possibleEnemyHQs[i*3+1] = new MapLocation(l.x,rc.getMapHeight()-l.y);
            possibleEnemyHQs[i*3+2] = new MapLocation(rc.getMapWidth()-l.x,rc.getMapHeight()-l.y);
        }
        int totalWeight = 0;
        for(int i=0;i<12;i++) {
            if(possibleEnemyHQs[i]==null) continue;
            totalWeight += possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        if(totalWeight==0) return null;
        int r = rng.nextInt(totalWeight);
        rc.setIndicatorString("w="+totalWeight+" r="+r);
        int i;
        for(i=0;r>=0;i++) {
            r -= possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        return possibleEnemyHQs[i-1];
    }
    public MapLocation getRandomKnownEnemyHQ() throws GameActionException {
        MapLocation[] possibleEnemyHQs = new MapLocation[4];
        for(int i=0;i<4;i++) {
            if(rc.readSharedArray(i+Robot.INDEX_ENEMY_HQ)>0)
                possibleEnemyHQs[i] = intToLoc(rc.readSharedArray(i+Robot.INDEX_ENEMY_HQ));
        }
        int totalWeight = 0;
        for(int i=0;i<4;i++) {
            if(possibleEnemyHQs[i]==null) continue;
            totalWeight += 1000000/possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        if(totalWeight==0) return null;
        int r = rng.nextInt(totalWeight);
        int i;
        for(i=0;r>=0;i++) {
            if(possibleEnemyHQs[i]==null) continue;
            r -= 1000000/possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        return possibleEnemyHQs[i-1];
    }
    public void updateEnemyHQs() throws GameActionException {
        MapLocation[] needsUpdating = new MapLocation[4];
        MapLocation[] newEnemyHQs = new MapLocation[4];
        int newIndex = 0;
        for(int i=0;i<4;i++) {
            MapLocation l = Robot.intToLoc(rc.readSharedArray(i+Robot.INDEX_ENEMY_HQ));
            if(rc.canSenseLocation(l))
                needsUpdating[i] = l;
        }
        for(RobotInfo r:rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent())) {
            if(r.type == RobotType.ARCHON) {
                int i;
                for(i=0;i<4;i++) {
                    if(r.location.equals(needsUpdating[i])) {//this accounts for an existing one
                        needsUpdating[i] = null;
                        break;
                    }
                }
                if(i==4) { //this is a new enemy HQ
                    newEnemyHQs[newIndex++] = r.location;
                }
            }
        }
        while(newIndex>0) {
            newIndex--;
            for(int i=0;i<4;i++) {
                if(rc.readSharedArray(i+Robot.INDEX_ENEMY_HQ)>0 && needsUpdating[i] == null)
                    continue;
                rc.writeSharedArray(i+Robot.INDEX_ENEMY_HQ, Robot.locToInt(newEnemyHQs[newIndex]));
                needsUpdating[i] = null;
                break;
            }
        }
        for(int i=0;i<4;i++) {
            if(needsUpdating[i]!=null)
                rc.writeSharedArray(i+Robot.INDEX_ENEMY_HQ, 0);
        }
    }
    void removeOldEnemySoldierLocations() throws GameActionException {
        for(int i=INDEX_ENEMY_LOCATION;i<INDEX_ENEMY_LOCATION+NUM_ENEMY_SOLDIER_CHUNKS;i++) {
            int x = rc.readSharedArray(i);
            if((x&0xff)==0xff) continue;
            if(((0x40+(rc.getRoundNum()&0x3f) - (x>>8))&0x3f) > 8 || rc.getRoundNum()<2)
                rc.writeSharedArray(i, 0xff);
        }
    }
    void updateEnemySoliderLocations() throws GameActionException {
        //MapLocation[] enemySoldiers = new MapLocation[NUM_ENEMY_SOLDIER_CHUNKS];
        int[] enemySoldierChunks = new int[NUM_ENEMY_SOLDIER_CHUNKS];
        
        for(int i=0;i<NUM_ENEMY_SOLDIER_CHUNKS;i++) {
            //enemySoldiers[i] = Robot.intToChunk(rc.readSharedArray(INDEX_ENEMY_LOCATION+i));
            int x = rc.readSharedArray(INDEX_ENEMY_LOCATION+i);
            enemySoldierChunks[i] = x&0xff;
            
        }
        for(RobotInfo r:rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent())) {
            if(r.type == RobotType.SOLDIER || r.type == RobotType.WATCHTOWER) {
                int x = Robot.chunkToInt(r.location);
                boolean found=false;
                for(int i=0;i<NUM_ENEMY_SOLDIER_CHUNKS;i++) {
                    if(enemySoldierChunks[i] == x) {
                        found=true;
                        break;
                    }
                }
                if(!found) {
                    for(int i=0;i<NUM_ENEMY_SOLDIER_CHUNKS;i++) {
                        if(enemySoldierChunks[i] == 0xff) {//0xff is the empty slot code
                            rc.writeSharedArray(i+Robot.INDEX_ENEMY_LOCATION, x | ((rc.getRoundNum()&0x3f)<<8));
                            return;
                        }
                    }
                    
                }
            }
        }
    }
    MapLocation getNearestEnemyChunk() throws GameActionException {
        MapLocation nearest = null;
        for(int i=0;i<NUM_ENEMY_SOLDIER_CHUNKS;i++) {
            int x1 = rc.readSharedArray(INDEX_ENEMY_LOCATION+i);
            if(x1==0xff) continue;
            MapLocation x = Robot.intToChunk(x1);
            if(nearest==null || rc.getLocation().distanceSquaredTo(x) < rc.getLocation().distanceSquaredTo(nearest)) {
                nearest = x;
            }
        }
        return nearest;
    }
}
