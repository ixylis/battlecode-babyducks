package matirold;

import battlecode.common.*;

import java.util.Random;

import static battlecode.common.RobotType.SOLDIER;
import static battlecode.common.RobotType.WATCHTOWER;
import static java.lang.Math.sqrt;

public abstract class Robot {
    // for bash testing: DO NOT ADD WHITESPACE
    public static final int SEED=27875;

    public static final int INDEX_MY_HQ=0; //4 ints for friendly HQ locations
    public static final int INDEX_ENEMY_HQ=4; //4 ints for known enemy HQ locs
    public static final int INDEX_LIVE_MINERS=8;
    public static final int INDEX_INCOME=9;
    public static final int INDEX_ENEMY_LOCATION=10;//10 ints for recent enemy soldier locations
    public static final int NUM_ENEMY_SOLDIER_CHUNKS=10;
    public static final int INDEX_HQ_SPENDING=20; //one bit for is alive, two bits for round num mod 4, remainder for total lead spent.
    public static final int INDEX_EXPLORED_CHUNKS=24; //4 ints (64 bits, one for each sections of map, divide map into 8 sections each way)
    public static final int NUM_GOOD_LOCS = 18; // this should be reduced if more ints are needed
    public static final int INDEX_GOOD_LOC = 28; // n approximate "good" (aka lead heavy) locations)
    public static final int INDEX_GOODLOC_WORTH = INDEX_GOOD_LOC + NUM_GOOD_LOCS; // measures of their "goodness"

    public static final double HEALTH_FACTOR = 0.2;
    public static final int MAX_LEAD=1000; // trigger to start building watchtowers
    
    int mapWidth;
    int mapHeight;    
    MapLocation myLoc;
    MapLocation[] corners;
    RobotType myType;
    Team Us, Them;

    abstract static class Weightage {abstract double weight(Direction d);}

    class RubbleWeight extends Weightage {

        @Override
        double weight(Direction d) {
            try {
                if(rc.canSenseLocation(myLoc.add(d)))
                    return 1000 / (10.0 + rc.senseRubble(myLoc.add(d)));
                else
                    return 0;
            } catch(GameActionException e) {
                e.printStackTrace();
            }

            return 0.0;
        }
    }
    RubbleWeight rubbleWeight = new RubbleWeight();

    class TargetWeight extends Weightage {

        MapLocation targetLoc;

        TargetWeight(MapLocation targetLoc) {
            this.targetLoc = targetLoc;
        }

        @Override
        double weight(Direction d) {
            MapLocation newLoc = myLoc.add(d);
            double curDist = myLoc.distanceSquaredTo(targetLoc);
            double newDist = newLoc.distanceSquaredTo(targetLoc);
            if(newDist > curDist) return 1;
            return (10 * (sqrt(curDist) - sqrt(newDist)) + 1) * (1 + rubbleWeight.weight(d));
        }
    }

    /*
     * intention is for each enemy seen within the last 20 rounds is in here
     * but only put distinct entries if they are more than 4 tiles apart.
     * when anyone sees an enemy check if it would be a new entry. if so add it with the round number.
     */
    
    public static final boolean DEBUG = true;
    public final Random rng;
    RobotController rc;
    Robot(RobotController r) throws GameActionException {
        rc = r;
        rng = new Random(rc.getID() + SEED);
        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        corners = new MapLocation[4];
        corners[0] = new MapLocation(0, 0);
        corners[1] = new MapLocation(mapWidth - 1, 0);
        corners[2] = new MapLocation(0, mapHeight - 1);
        corners[3] = new MapLocation(mapWidth - 1, mapHeight - 1);
//        corners[4] = new MapLocation(mapWidth / 2, 0);
//        corners[5] = new MapLocation(0, mapHeight / 2);
//        corners[6] = new MapLocation(mapWidth / 2, mapHeight / 2);
//        corners[7] = new MapLocation(mapWidth / 2, mapHeight / 2);
//        corners[8] = new MapLocation(mapWidth - 1, mapHeight / 2);
        myType = rc.getType();
        Us = rc.getTeam();
        Them = Us.opponent();

        if(rc.readSharedArray(INDEX_GOODLOC_WORTH) == 0) {
            for(int i=0;i<4;i++) {
                rc.writeSharedArray(INDEX_GOOD_LOC + 0, locToInt(corners[i]));
                rc.writeSharedArray(INDEX_GOODLOC_WORTH + i, 10);
            }

            for(int i = 0; i < NUM_GOOD_LOCS; i++) {
                rc.writeSharedArray(INDEX_GOOD_LOC + i, 
                        locToInt(randLoc()));
                rc.writeSharedArray(INDEX_GOODLOC_WORTH + i, 1);
            }
        }
    }
    
    public MapLocation randLoc() {
        return new MapLocation(rng.nextInt(mapWidth), rng.nextInt(mapHeight));
    }

    MapLocation[] recentLocations=new MapLocation[10];
    int recentLocationsIndex = 0;
    int lastMoveTurn = 0;
    void run() {
        while(true) {
            try {
                myLoc = rc.getLocation();
                turn();
                if(!rc.getLocation().equals(recentLocations[recentLocationsIndex])) {
                    recentLocationsIndex = (recentLocationsIndex + 1)%10;
                    recentLocations[recentLocationsIndex] = rc.getLocation();
                    lastMoveTurn = rc.getRoundNum();
                }
                updateInfo();
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
    
    public void updateInfo() throws GameActionException {
        if(Clock.getBytecodesLeft() < rc.getType().bytecodeLimit - 2000) // too few
            return;
        
        updateEnemyHQs();
        updateEnemySoliderLocations();
        updateGoodLocs();
    }

    public void moveInDirection(Direction d) throws GameActionException {
        Direction moveDir = bestMove(d);
        if(rc.canMove(moveDir))
            rc.move(moveDir);
    }

    public Direction bestMove(Direction d) throws GameActionException {
        Direction[] dd = {d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight()};
        double[] suitability = {1,.5,.5,.1,.1};
        for(int i=0;i<5;i++) {
            MapLocation l = rc.getLocation().add(dd[i]);
            if(rc.onTheMap(l))
                suitability[i] /= 10 + rc.senseRubble(l);

        }
        double best = 0;
        Direction bestD = Direction.CENTER;
        for(int i=0;i<5;i++) {
            if(suitability[i]>best && rc.canMove(dd[i])) {
                best = suitability[i];
                bestD = dd[i];
            }
        }
        return bestD;
    }

    int frustration=0;
    MapLocation lastMoveTowardTarget;
    public void moveToward(MapLocation to) throws GameActionException {
        if(Robot.DEBUG) {
            rc.setIndicatorLine(rc.getLocation(), to, 255, 255, 0);
            //System.out.println("Navigating toward " + l);
        }
        MapLocation me = rc.getLocation();
        int dx = to.x - me.x, dy = to.y - me.y;
        Direction ideal=null;
        Direction ok=null, ok2=null;
        Direction mediocre=null, mediocre2=null;
        Direction sad=null, sad2=null;
        boolean onDiagonal=false;
        boolean onEdge=false;
        if(dx < 0) {
            if(dy < 0) {
                ideal = Direction.SOUTHWEST;
                if(dx < dy) {
                    ok = Direction.WEST;
                    ok2 = Direction.NORTHWEST;
                    mediocre = Direction.SOUTH;
                } else if(dx > dy){
                    ok = Direction.SOUTH;
                    ok2 = Direction.SOUTHEAST;
                    mediocre = Direction.WEST;
                } else {
                    onDiagonal = true;
                    ok = Direction.WEST;
                    ok2 = Direction.SOUTH;
                    mediocre = Direction.NORTHWEST;
                    mediocre2 = Direction.SOUTHEAST;
                }
            } else if(dy > 0) {
                ideal = Direction.NORTHWEST;
                if(dx < -dy) {
                    ok = Direction.WEST;
                    ok2 = Direction.SOUTHWEST;
                    mediocre = Direction.NORTH;
                } else if(dx > -dy){
                    ok = Direction.NORTH;
                    ok2 = Direction.NORTHEAST;
                    mediocre = Direction.WEST;
                } else {
                    onDiagonal = true;
                    ok = Direction.WEST;
                    ok2 = Direction.NORTH;
                    mediocre = Direction.NORTHEAST;
                    mediocre2 = Direction.SOUTHWEST;
                }
            } else {
                ideal = Direction.WEST;
                ok = Direction.NORTHWEST;
                ok2 = Direction.SOUTHWEST;
                mediocre = Direction.NORTH;
                mediocre2 = Direction.SOUTH;
                onEdge = true;
            }
        } else if(dx > 0) {
            if(dy < 0) {
                ideal = Direction.SOUTHEAST;
                if(-dx < dy) {
                    ok = Direction.EAST;
                    ok2 = Direction.NORTHEAST;
                    mediocre = Direction.SOUTH;
                } else if(-dx > dy){
                    ok = Direction.SOUTH;
                    ok2 = Direction.SOUTHWEST;
                    mediocre = Direction.EAST;
                } else {
                    onDiagonal = true;
                    ok = Direction.EAST;
                    ok2 = Direction.SOUTH;
                    mediocre = Direction.NORTHEAST;
                    mediocre2 = Direction.SOUTHWEST;
                }
            } else if(dy > 0) {
                ideal = Direction.NORTHEAST;
                if(-dx < -dy) {
                    ok = Direction.EAST;
                    ok2 = Direction.SOUTHEAST;
                    mediocre = Direction.NORTH;
                } else if(-dx > -dy){
                    ok = Direction.NORTH;
                    ok2 = Direction.NORTHWEST;
                    mediocre = Direction.EAST;
                } else {
                    onDiagonal = true;
                    ok = Direction.EAST;
                    ok2 = Direction.NORTH;
                    mediocre = Direction.NORTHWEST;
                    mediocre2 = Direction.SOUTHEAST;
                }
            } else {
                ideal = Direction.EAST;
                ok = Direction.NORTHEAST;
                ok2 = Direction.SOUTHEAST;
                mediocre = Direction.NORTH;
                mediocre2 = Direction.SOUTH;
                onEdge = true;
            }
        } else {
            if(dy < 0) {
                ideal = Direction.SOUTH;
                ok = Direction.SOUTHWEST;
                ok2 = Direction.SOUTHEAST;
                mediocre = Direction.WEST;
                mediocre2 = Direction.EAST;
                onEdge = true;
            } else if(dy > 0) {
                ideal = Direction.NORTH;
                ok = Direction.NORTHWEST;
                ok2 = Direction.NORTHEAST;
                mediocre = Direction.WEST;
                mediocre2 = Direction.EAST;
                onEdge = true;
            } else {
                return; //we are at the destination!
            }
        }
        MapLocation recentLoc = recentLocations[(recentLocationsIndex + 9)%10];
        Direction lastMoveDir = (recentLoc==null || to!=lastMoveTowardTarget)?null:me.directionTo(recentLoc);
        lastMoveTowardTarget = to;
        while(frustration < 111) {
            rc.setIndicatorString("frustration "+frustration+" "+ideal);
            Direction d = ideal;
            if(lastMoveDir != d && d != null && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration) {
                rc.move(d);
                frustration = 0;
                return;
            }
            d = ok;
            if(lastMoveDir != d && d != null && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration) {
                rc.move(d);
                frustration = onDiagonal?15:5;
                return;
            }
            d = ok2;
            if(lastMoveDir != d && d != null && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration) {
                rc.move(d);
                frustration = onDiagonal?15:5;
                return;
            }
            d = mediocre;
            if(lastMoveDir != d && d != null && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration) {
                rc.move(d);
                frustration += onEdge?20:15;
                return;
            }
            d = mediocre2;
            if(lastMoveDir != d && d != null && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration) {
                rc.move(d);
                frustration += 20;
                return;
            }
            frustration += 10;
        }
        frustration = 100;
    }
    public void moveTowardOld(MapLocation l) throws GameActionException {
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

    public void moveTowardMatir(MapLocation l) throws GameActionException {
        moveTowardMatir(l, 0.05);
    }

    public void moveTowardMatir(MapLocation l, double randomness) throws GameActionException {
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
        Direction d = rc.getLocation().directionTo(l);
        Direction[] dd = {d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight()};
        Direction moveDir = rng.nextDouble() < randomness ?
                randDirByWeight(dd, new TargetWeight(l)) : bestMove(d);
        if(rc.canMove(moveDir)) rc.move(moveDir);
    }

    public Direction randDirByWeight(Direction[] dirs, Weightage wt) {

        double[] cwt = new double[dirs.length+1];
        cwt[0] = 0;

        for(int i=1;i<=dirs.length;++i)
        {
            cwt[i] = cwt[i-1] + wt.weight(dirs[i-1]);
        }

        if(cwt[dirs.length] == 0) return null;

        for(int i=1;i<=dirs.length;++i)
        {
            cwt[i] /= cwt[dirs.length];
        }

        double r = rng.nextDouble();
        int i = 0;
        while(cwt[i] < r) i++;

        return dirs[i-1];
    }

    private MapLocation wanderTarget=null;
    private int lastWanderProgress = 0; //the last turn which we wandered closer to our destination
    public void wander() throws GameActionException {
        while(wanderTarget==null || rc.getLocation().distanceSquaredTo(wanderTarget)<10) {
            wanderTarget = rng.nextDouble() < 0.5 ?
                    corners[rng.nextInt(4)] :
                    new MapLocation(rng.nextInt(mapWidth), rng.nextInt(mapHeight));
            lastWanderProgress = rc.getRoundNum();
        }
        MapLocation old = rc.getLocation();
        moveTowardMatir(wanderTarget, 1);
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
            possibleEnemyHQs[i*3] = new MapLocation(mapWidth-1-l.x,l.y);
            possibleEnemyHQs[i*3+1] = new MapLocation(l.x,mapHeight-1-l.y);
            possibleEnemyHQs[i*3+2] = new MapLocation(mapWidth-1-l.x,mapHeight-1-l.y);
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
            if(r.type == RobotType.SOLDIER || r.type == RobotType.WATCHTOWER || r.type == RobotType.MINER) {
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

    MapLocation getNearestUnexploredChunk(MapLocation l) throws GameActionException {
        int x0 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+0);
        int x1 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+1);
        int x2 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+2);
        int x3 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+3);
        int mh = mapHeight, mw = mapWidth;
        MapLocation m;
        MapLocation best = null;
        int bestD = 9999;
        if((x0 & 0x1) == 0 && (m = new MapLocation(mw*1/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x2) == 0 && (m = new MapLocation(mw*3/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x4) == 0 && (m = new MapLocation(mw*5/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x8) == 0 && (m = new MapLocation(mw*7/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x10) == 0 && (m = new MapLocation(mw*9/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x20) == 0 && (m = new MapLocation(mw*11/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x40) == 0 && (m = new MapLocation(mw*13/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x80) == 0 && (m = new MapLocation(mw*15/16,mh*1/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x100) == 0 && (m = new MapLocation(mw*1/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x200) == 0 && (m = new MapLocation(mw*3/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x400) == 0 && (m = new MapLocation(mw*5/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x800) == 0 && (m = new MapLocation(mw*7/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x1000) == 0 && (m = new MapLocation(mw*9/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x2000) == 0 && (m = new MapLocation(mw*11/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x4000) == 0 && (m = new MapLocation(mw*13/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x0 & 0x8000) == 0 && (m = new MapLocation(mw*15/16,mh*3/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x1) == 0 && (m = new MapLocation(mw*1/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x2) == 0 && (m = new MapLocation(mw*3/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x4) == 0 && (m = new MapLocation(mw*5/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x8) == 0 && (m = new MapLocation(mw*7/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x10) == 0 && (m = new MapLocation(mw*9/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x20) == 0 && (m = new MapLocation(mw*11/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x40) == 0 && (m = new MapLocation(mw*13/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x80) == 0 && (m = new MapLocation(mw*15/16,mh*5/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x100) == 0 && (m = new MapLocation(mw*1/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x200) == 0 && (m = new MapLocation(mw*3/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x400) == 0 && (m = new MapLocation(mw*5/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x800) == 0 && (m = new MapLocation(mw*7/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x1000) == 0 && (m = new MapLocation(mw*9/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x2000) == 0 && (m = new MapLocation(mw*11/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x4000) == 0 && (m = new MapLocation(mw*13/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x1 & 0x8000) == 0 && (m = new MapLocation(mw*15/16,mh*7/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x1) == 0 && (m = new MapLocation(mw*1/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x2) == 0 && (m = new MapLocation(mw*3/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x4) == 0 && (m = new MapLocation(mw*5/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x8) == 0 && (m = new MapLocation(mw*7/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x10) == 0 && (m = new MapLocation(mw*9/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x20) == 0 && (m = new MapLocation(mw*11/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x40) == 0 && (m = new MapLocation(mw*13/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x80) == 0 && (m = new MapLocation(mw*15/16,mh*9/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x100) == 0 && (m = new MapLocation(mw*1/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x200) == 0 && (m = new MapLocation(mw*3/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x400) == 0 && (m = new MapLocation(mw*5/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x800) == 0 && (m = new MapLocation(mw*7/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x1000) == 0 && (m = new MapLocation(mw*9/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x2000) == 0 && (m = new MapLocation(mw*11/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x4000) == 0 && (m = new MapLocation(mw*13/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x2 & 0x8000) == 0 && (m = new MapLocation(mw*15/16,mh*11/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x1) == 0 && (m = new MapLocation(mw*1/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x2) == 0 && (m = new MapLocation(mw*3/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x4) == 0 && (m = new MapLocation(mw*5/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x8) == 0 && (m = new MapLocation(mw*7/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x10) == 0 && (m = new MapLocation(mw*9/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x20) == 0 && (m = new MapLocation(mw*11/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x40) == 0 && (m = new MapLocation(mw*13/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x80) == 0 && (m = new MapLocation(mw*15/16,mh*13/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x100) == 0 && (m = new MapLocation(mw*1/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x200) == 0 && (m = new MapLocation(mw*3/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x400) == 0 && (m = new MapLocation(mw*5/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x800) == 0 && (m = new MapLocation(mw*7/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x1000) == 0 && (m = new MapLocation(mw*9/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x2000) == 0 && (m = new MapLocation(mw*11/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x4000) == 0 && (m = new MapLocation(mw*13/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}
        if((x3 & 0x8000) == 0 && (m = new MapLocation(mw*15/16,mh*15/16)).distanceSquaredTo(l) < bestD) {best = m;bestD = m.distanceSquaredTo(l);}

        return best;
    }
    void writeUnexploredChunk() throws GameActionException {
        MapLocation l = rc.getLocation();
        int k = l.x*8/mapWidth + (l.y*8/mapHeight)*8;
        //rc.setIndicatorString(""+k);
        int i=k/16, j = k%16;
        //rc.setIndicatorString(Integer.toBinaryString(rc.readSharedArray(INDEX_EXPLORED_CHUNKS+0))+" "+Integer.toBinaryString(rc.readSharedArray(INDEX_EXPLORED_CHUNKS+1))
        //+" "+rc.readSharedArray(INDEX_EXPLORED_CHUNKS+2)+" "+rc.readSharedArray(INDEX_EXPLORED_CHUNKS+3)+" "+i+" "+j);
        if((rc.readSharedArray(INDEX_EXPLORED_CHUNKS+i) & (1<<j)) == 0)
            rc.writeSharedArray(INDEX_EXPLORED_CHUNKS+i, rc.readSharedArray(INDEX_EXPLORED_CHUNKS+i) | (1<<j));
    }
    void clearUnexploredChunks() throws GameActionException {
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS+0, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS+1, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS+2, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS+3, 0);
    }
    
    void updateGoodLocs() throws GameActionException {
        MapLocation[] curLocs = new MapLocation[NUM_GOOD_LOCS];
        int[] goodness = new int[NUM_GOOD_LOCS];
        int worst = 0;
        double comdx = 0, comdy = 0;
        int mass = 0;
        
        for(int i = 0; i < NUM_GOOD_LOCS; i++) {
            curLocs[i] = intToLoc(rc.readSharedArray(INDEX_GOOD_LOC + i));
            goodness[i] = rc.readSharedArray(INDEX_GOODLOC_WORTH + i);
            
            if(goodness[i] < goodness[worst]) {
                worst = i;
            }
        }
        
        MapLocation[] leads = rc.senseNearbyLocationsWithLead();
        
        for(MapLocation loc : leads) {
            int amt = rc.senseLead(loc);
            comdx = ((loc.x - myLoc.x) * amt + comdx * mass) / (amt + mass);
            comdy = ((loc.y - myLoc.y) * amt + comdy * mass) / (amt + mass);
            mass += amt;
        }
        
        MapLocation com = myLoc.translate((int) comdx, (int) comdy);
        double value = 0;
        for(MapLocation loc : leads) {
            value += 100 * rc.senseLead(loc) / (1 + sqrt(com.distanceSquaredTo(loc)));
        }

        if(goodness[worst] > value) { // even the worst old location is better
            return;
        }

        int conflicts = 0;
        int confi = 0;

        for(int i = 0; i < NUM_GOOD_LOCS; i++) {
            if(com.distanceSquaredTo(curLocs[i]) < 25) {
                conflicts++;
                confi = i;
            }
        }

        if(conflicts >= 2) return; // better to keep more separate locations

        int chosen = worst;
        if(conflicts == 1) {
            if(goodness[confi] < value) {
                // new position better
                chosen = confi;
            }
        }

        rc.writeSharedArray(INDEX_GOOD_LOC + chosen, locToInt(com));
        rc.writeSharedArray(INDEX_GOODLOC_WORTH + chosen, (int) value);

        rc.setIndicatorDot(com, 0, 0, 0);
    }

    public MapLocation getNearestGoodLocation(MapLocation m) throws GameActionException {
        MapLocation bestLoc = null;
        double bestVal = 0;

        for(int i = 0; i <= NUM_GOOD_LOCS; i++) {
            MapLocation loc = intToLoc(rc.readSharedArray(INDEX_GOOD_LOC + i));
            double value = rc.readSharedArray(INDEX_GOODLOC_WORTH + i) /
                    sqrt(m.distanceSquaredTo(loc));
            if(value > bestVal) {
                bestVal = value;
                bestLoc = loc;
            }
        }

        return bestLoc;
    }

    public int computeStrength(RobotInfo[] robots) throws GameActionException {
        int strength = 0;

        for(RobotInfo r: robots) {
            if(r.getType() == SOLDIER || r.getType() == WATCHTOWER)
                strength += 1000 / (10 + rc.senseRubble(r.location)) + r.health * HEALTH_FACTOR;
        }

        return strength;
    }
}
