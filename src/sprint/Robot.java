package sprint;

import battlecode.common.*;

import java.util.Random;

import static battlecode.common.RobotType.SOLDIER;
import static battlecode.common.RobotType.WATCHTOWER;
import static java.lang.Math.sqrt;

public abstract class Robot {
    public static final int SEED=0;
    public static final int INDEX_MY_HQ=0; //4 ints for friendly HQ locations
    public static final int INDEX_ENEMY_HQ=4; //4 ints for known enemy HQ locs
    public static final int INDEX_LIVE_MINERS=8;
    public static final int INDEX_INCOME=9;
    public static final int INDEX_ENEMY_LOCATION=10;//10 ints for recent enemy soldier locations
    public static final int NUM_ENEMY_SOLDIER_CHUNKS=10;
    public static final int INDEX_HQ_SPENDING=20; //one bit for is alive, two bits for round num mod 4, remainder for total lead spent.
    public static final int MAX_LEAD=1000; // trigger to start building watchtowers
    public static final int INDEX_EXPLORED_CHUNKS=24; //4 ints (64 bits, one for each sections of map, divide map into 8 sections each way)
    public static final double HEALTH_FACTOR = 0.2;
    MapLocation myLoc;
    MapLocation[] corners;

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
    
    public static final boolean DEBUG=true;
    public final Random rng;
    RobotController rc;
    Robot(RobotController r) throws GameActionException {
        rc = r;
        rng = new Random(SEED + rc.getID());
        corners = new MapLocation[4];
        corners[0] = new MapLocation(0, 0);
        corners[1] = new MapLocation(rc.getMapWidth() - 1, 0);
        corners[2] = new MapLocation(0, rc.getMapHeight() - 1);
        corners[3] = new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() - 1);
//        corners[4] = new MapLocation(rc.getMapWidth() / 2, 0);
//        corners[5] = new MapLocation(0, rc.getMapHeight() / 2);
//        corners[6] = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
//        corners[7] = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
//        corners[8] = new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() / 2);
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
                if(DEBUG) {
                    MapLocation last = rc.getLocation();
                    for(int i=recentLocationsIndex+9;i>recentLocationsIndex;i--) {
                        if(recentLocations[i%10]!=null) {
                            MapLocation next = recentLocations[i%10];
                            rc.setIndicatorLine(last, next, 255, 0, 0);
                            last=next;
                        }
                    }
                }
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
    int frustration=10;
    MapLocation lastMoveTowardTarget;
    public void moveToward(MapLocation to) throws GameActionException {
        nav(to);
        return;
    }
    public void moveTowardOld2(MapLocation to) throws GameActionException {
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
        Direction oldD = rc.getLocation().directionTo(lastMoveTowardTarget);
        boolean veryDiffDirection = oldD == null || (oldD != ideal && oldD.rotateLeft() != ideal && oldD.rotateRight() != ideal && oldD.rotateLeft().rotateLeft() != ideal && oldD.rotateRight().rotateRight() != ideal);
        Direction lastMoveDir = (recentLoc==null || veryDiffDirection)?null:me.directionTo(recentLoc);
        lastMoveTowardTarget = to;
        int dist = Math.max(dx, dy);
        while(frustration < 200) {
            Direction d = ideal;
            if(d != null && lastMoveDir != d && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration - 10) {
                rc.move(d);
                frustration = 10;
                break;
            }
            d = ok;
            if(d != null && lastMoveDir != d && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration - 10) {
                rc.move(d);
                //frustration = onDiagonal? frustration*dist/(dist+1) : frustration * 2/3;
                frustration += onDiagonal?15:5;
                break;
            }
            d = ok2;
            if(d != null && lastMoveDir != d && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration - 10) {
                rc.move(d);
                //frustration = onDiagonal? frustration*dist/(dist+1) : frustration * 2/3;
                frustration += onDiagonal?15:5;
                break;
            }
            d = mediocre;
            if(d != null && lastMoveDir != d && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration - 10) {
                rc.move(d);
                //frustration = onEdge? frustration*dist/(dist+2) : frustration * 8/7;
                frustration += onEdge?20:15;
                break;
            }
            d = mediocre2;
            if(d != null && lastMoveDir != d && rc.canMove(d) && rc.senseRubble(me.add(d)) <= frustration - 10) {
                rc.move(d);
                //frustration = onEdge? frustration*dist/(dist+2) : frustration * 8/7;
                frustration += onEdge?20:15;
                break;
            }
            frustration += 10;
        }
        if(!rc.isMovementReady())
            frustration = Math.min(150, frustration);
        else
            frustration = 150;
        rc.setIndicatorString("frustration "+frustration+" ideal "+ideal+" last "+lastMoveDir);
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
                    new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
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
            possibleEnemyHQs[i*3] = new MapLocation(rc.getMapWidth()-1-l.x,l.y);
            possibleEnemyHQs[i*3+1] = new MapLocation(l.x,rc.getMapHeight()-1-l.y);
            possibleEnemyHQs[i*3+2] = new MapLocation(rc.getMapWidth()-1-l.x,rc.getMapHeight()-1-l.y);
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
        int mh = rc.getMapHeight(), mw = rc.getMapWidth();
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
    void displayUnexploredChunks() throws GameActionException {
        int x0 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+0);
        int x1 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+1);
        int x2 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+2);
        int x3 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS+3);
        int mh = rc.getMapHeight(), mw = rc.getMapWidth();
        if((x0 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*1/16), 0, 0, 255);
        if((x0 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*3/16), 0, 0, 255);
        if((x0 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*3/16), 0, 0, 255);
        if((x1 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*5/16), 0, 0, 255);
        if((x1 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*7/16), 0, 0, 255);
        if((x1 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*7/16), 0, 0, 255);
        if((x2 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*9/16), 0, 0, 255);
        if((x2 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*11/16), 0, 0, 255);
        if((x2 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*11/16), 0, 0, 255);
        if((x3 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*13/16), 0, 0, 255);
        if((x3 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw*1/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw*3/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw*5/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw*7/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw*9/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw*11/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw*13/16,mh*15/16), 0, 0, 255);
        if((x3 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw*15/16,mh*15/16), 0, 0, 255);
    }
    void writeUnexploredChunk() throws GameActionException {
        MapLocation l = rc.getLocation();
        int k = l.x*8/rc.getMapWidth() + (l.y*8/rc.getMapHeight())*8;
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

    public int computeStrength(RobotInfo[] robots) throws GameActionException {
        int strength = 0;

        for(RobotInfo r: robots) {
            if(r.getType() == SOLDIER || r.getType() == WATCHTOWER)
                strength += 1000 / (10 + rc.senseRubble(r.location)) + r.health * HEALTH_FACTOR;
        }

        return strength;
    }
    

    public void nav(MapLocation to) throws GameActionException {
        int[] rawCosts;
        /*
         * rawCosts[loc] = (rubble + 10) * (2 if occupied else 1)
         * double the cost for each space occupied by a robot
         */
        
        //int[] heap = new int [121];
        //int[] costs = new int[121];
        //int heapi;
        /*
         * start at the center, which is (5,5)
         * a location is x*11 + y
         */
        //costs[5*11+5] = 0;
        /*
         * for all of the 3x3 in the middle, cost is simply rubble+10
         */
        //heap[0] = 4*11+4;
        //heap[1] = 4*11+5; if(costs[heap[1]] < costs[heap[0]]) {int tmp = heap[0]; heap[0] = heap[1]; heap[1] = tmp;}

        int b; //best cost
        //int i; //best cost index
        /*
         * bfs from outside inward
         * for each square, it's cost is the min of all adjacent cost + it's raw cost
         * for computing this min, a square outside of vision range is always cost = euclidean distance*10
         */
        

        int b0 = Clock.getBytecodeNum();
        
        MapLocation me = rc.getLocation();
        int myx = me.x, myy = me.y;
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();
        
        boolean onMapX7 = true;boolean onMapY7 = true;
        boolean onMapX2 = myx + -5 >= 0;boolean onMapY2 = myy + -5 >= 0;
        boolean onMapX3 = myx + -4 >= 0;boolean onMapY3 = myy + -4 >= 0;
        boolean onMapX4 = myx + -3 >= 0;boolean onMapY4 = myy + -3 >= 0;
        boolean onMapX5 = myx + -2 >= 0;boolean onMapY5 = myy + -2 >= 0;
        boolean onMapX6 = myx + -1 >= 0;boolean onMapY6 = myy + -1 >= 0;
        boolean onMapX8 = myx + 1 < mapWidth;boolean onMapY8 = myy + 1 < mapHeight;
        boolean onMapX9 = myx + 2 < mapWidth;boolean onMapY9 = myy + 2 < mapHeight;
        boolean onMapXa = myx + 3 < mapWidth;boolean onMapYa = myy + 3 < mapHeight;
        boolean onMapXb = myx + 4 < mapWidth;boolean onMapYb = myy + 4 < mapHeight;
        boolean onMapXc = myx + 5 < mapWidth;boolean onMapYc = myy + 5 < mapHeight;
        int r35 = (onMapX3 && onMapY5)?rc.senseRubble(me.translate(-4,-2))+10:1000;
        int r39 = (onMapX3 && onMapY9)?rc.senseRubble(me.translate(-4,2))+10:1000;
        int r53 = (onMapX5 && onMapY3)?rc.senseRubble(me.translate(-2,-4))+10:1000;
        int r5b = (onMapX5 && onMapYb)?rc.senseRubble(me.translate(-2,4))+10:1000;
        int r93 = (onMapX9 && onMapY3)?rc.senseRubble(me.translate(2,-4))+10:1000;
        int r9b = (onMapX9 && onMapYb)?rc.senseRubble(me.translate(2,4))+10:1000;
        int rb5 = (onMapXb && onMapY5)?rc.senseRubble(me.translate(4,-2))+10:1000;
        int rb9 = (onMapXb && onMapY9)?rc.senseRubble(me.translate(4,2))+10:1000;
        int r44 = (onMapX4 && onMapY4)?rc.senseRubble(me.translate(-3,-3))+10:1000;
        int r4a = (onMapX4 && onMapYa)?rc.senseRubble(me.translate(-3,3))+10:1000;
        int ra4 = (onMapXa && onMapY4)?rc.senseRubble(me.translate(3,-3))+10:1000;
        int raa = (onMapXa && onMapYa)?rc.senseRubble(me.translate(3,3))+10:1000;
        int r36 = (onMapX3 && onMapY6)?rc.senseRubble(me.translate(-4,-1))+10:1000;
        int r38 = (onMapX3 && onMapY8)?rc.senseRubble(me.translate(-4,1))+10:1000;
        int r63 = (onMapX6 && onMapY3)?rc.senseRubble(me.translate(-1,-4))+10:1000;
        int r6b = (onMapX6 && onMapYb)?rc.senseRubble(me.translate(-1,4))+10:1000;
        int r83 = (onMapX8 && onMapY3)?rc.senseRubble(me.translate(1,-4))+10:1000;
        int r8b = (onMapX8 && onMapYb)?rc.senseRubble(me.translate(1,4))+10:1000;
        int rb6 = (onMapXb && onMapY6)?rc.senseRubble(me.translate(4,-1))+10:1000;
        int rb8 = (onMapXb && onMapY8)?rc.senseRubble(me.translate(4,1))+10:1000;
        int r37 = (onMapX3 && onMapY7)?rc.senseRubble(me.translate(-4,0))+10:1000;
        int r73 = (onMapX7 && onMapY3)?rc.senseRubble(me.translate(0,-4))+10:1000;
        int r7b = (onMapX7 && onMapYb)?rc.senseRubble(me.translate(0,4))+10:1000;
        int rb7 = (onMapXb && onMapY7)?rc.senseRubble(me.translate(4,0))+10:1000;
        int r45 = (onMapX4 && onMapY5)?rc.senseRubble(me.translate(-3,-2))+10:1000;
        int r49 = (onMapX4 && onMapY9)?rc.senseRubble(me.translate(-3,2))+10:1000;
        int r54 = (onMapX5 && onMapY4)?rc.senseRubble(me.translate(-2,-3))+10:1000;
        int r5a = (onMapX5 && onMapYa)?rc.senseRubble(me.translate(-2,3))+10:1000;
        int r94 = (onMapX9 && onMapY4)?rc.senseRubble(me.translate(2,-3))+10:1000;
        int r9a = (onMapX9 && onMapYa)?rc.senseRubble(me.translate(2,3))+10:1000;
        int ra5 = (onMapXa && onMapY5)?rc.senseRubble(me.translate(3,-2))+10:1000;
        int ra9 = (onMapXa && onMapY9)?rc.senseRubble(me.translate(3,2))+10:1000;
        int r46 = (onMapX4 && onMapY6)?rc.senseRubble(me.translate(-3,-1))+10:1000;
        int r48 = (onMapX4 && onMapY8)?rc.senseRubble(me.translate(-3,1))+10:1000;
        int r64 = (onMapX6 && onMapY4)?rc.senseRubble(me.translate(-1,-3))+10:1000;
        int r6a = (onMapX6 && onMapYa)?rc.senseRubble(me.translate(-1,3))+10:1000;
        int r84 = (onMapX8 && onMapY4)?rc.senseRubble(me.translate(1,-3))+10:1000;
        int r8a = (onMapX8 && onMapYa)?rc.senseRubble(me.translate(1,3))+10:1000;
        int ra6 = (onMapXa && onMapY6)?rc.senseRubble(me.translate(3,-1))+10:1000;
        int ra8 = (onMapXa && onMapY8)?rc.senseRubble(me.translate(3,1))+10:1000;
        int r47 = (onMapX4 && onMapY7)?rc.senseRubble(me.translate(-3,0))+10:1000;
        int r74 = (onMapX7 && onMapY4)?rc.senseRubble(me.translate(0,-3))+10:1000;
        int r7a = (onMapX7 && onMapYa)?rc.senseRubble(me.translate(0,3))+10:1000;
        int ra7 = (onMapXa && onMapY7)?rc.senseRubble(me.translate(3,0))+10:1000;
        int r55 = (onMapX5 && onMapY5)?rc.senseRubble(me.translate(-2,-2))+10:1000;
        int r59 = (onMapX5 && onMapY9)?rc.senseRubble(me.translate(-2,2))+10:1000;
        int r95 = (onMapX9 && onMapY5)?rc.senseRubble(me.translate(2,-2))+10:1000;
        int r99 = (onMapX9 && onMapY9)?rc.senseRubble(me.translate(2,2))+10:1000;
        int r56 = (onMapX5 && onMapY6)?rc.senseRubble(me.translate(-2,-1))+10:1000;
        int r58 = (onMapX5 && onMapY8)?rc.senseRubble(me.translate(-2,1))+10:1000;
        int r65 = (onMapX6 && onMapY5)?rc.senseRubble(me.translate(-1,-2))+10:1000;
        int r69 = (onMapX6 && onMapY9)?rc.senseRubble(me.translate(-1,2))+10:1000;
        int r85 = (onMapX8 && onMapY5)?rc.senseRubble(me.translate(1,-2))+10:1000;
        int r89 = (onMapX8 && onMapY9)?rc.senseRubble(me.translate(1,2))+10:1000;
        int r96 = (onMapX9 && onMapY6)?rc.senseRubble(me.translate(2,-1))+10:1000;
        int r98 = (onMapX9 && onMapY8)?rc.senseRubble(me.translate(2,1))+10:1000;
        int r57 = (onMapX5 && onMapY7)?rc.senseRubble(me.translate(-2,0))+10:1000;
        int r75 = (onMapX7 && onMapY5)?rc.senseRubble(me.translate(0,-2))+10:1000;
        int r79 = (onMapX7 && onMapY9)?rc.senseRubble(me.translate(0,2))+10:1000;
        int r97 = (onMapX9 && onMapY7)?rc.senseRubble(me.translate(2,0))+10:1000;
        int r66 = (onMapX6 && onMapY6)?rc.senseRubble(me.translate(-1,-1))+10:1000;
        int r68 = (onMapX6 && onMapY8)?rc.senseRubble(me.translate(-1,1))+10:1000;
        int r86 = (onMapX8 && onMapY6)?rc.senseRubble(me.translate(1,-1))+10:1000;
        int r88 = (onMapX8 && onMapY8)?rc.senseRubble(me.translate(1,1))+10:1000;
        int r67 = (onMapX6 && onMapY7)?rc.senseRubble(me.translate(-1,0))+10:1000;
        int r76 = (onMapX7 && onMapY6)?rc.senseRubble(me.translate(0,-1))+10:1000;
        int r78 = (onMapX7 && onMapY8)?rc.senseRubble(me.translate(0,1))+10:1000;
        int r87 = (onMapX8 && onMapY7)?rc.senseRubble(me.translate(1,0))+10:1000;
        int r77 = (onMapX7 && onMapY7)?rc.senseRubble(me.translate(0,0))+10:1000;
        
        //bytecode so far = 1553;
        int b1 = Clock.getBytecodeNum();
        
        for(RobotInfo r : rc.senseNearbyRobots(20)) {
            switch((myx - r.location.x)*13 + (myy - r.location.y)) {
            case -54:r35*=2;continue;
            case -50:r39*=2;continue;
            case -30:r53*=2;continue;
            case -22:r5b*=2;continue;
            case 22:r93*=2;continue;
            case 30:r9b*=2;continue;
            case 50:rb5*=2;continue;
            case 54:rb9*=2;continue;
            case -42:r44*=2;continue;
            case -36:r4a*=2;continue;
            case 36:ra4*=2;continue;
            case 42:raa*=2;continue;
            case -53:r36*=2;continue;
            case -51:r38*=2;continue;
            case -17:r63*=2;continue;
            case -9:r6b*=2;continue;
            case 9:r83*=2;continue;
            case 17:r8b*=2;continue;
            case 51:rb6*=2;continue;
            case 53:rb8*=2;continue;
            case -52:r37*=2;continue;
            case -4:r73*=2;continue;
            case 4:r7b*=2;continue;
            case 52:rb7*=2;continue;
            case -41:r45*=2;continue;
            case -37:r49*=2;continue;
            case -29:r54*=2;continue;
            case -23:r5a*=2;continue;
            case 23:r94*=2;continue;
            case 29:r9a*=2;continue;
            case 37:ra5*=2;continue;
            case 41:ra9*=2;continue;
            case -40:r46*=2;continue;
            case -38:r48*=2;continue;
            case -16:r64*=2;continue;
            case -10:r6a*=2;continue;
            case 10:r84*=2;continue;
            case 16:r8a*=2;continue;
            case 38:ra6*=2;continue;
            case 40:ra8*=2;continue;
            case -39:r47*=2;continue;
            case -3:r74*=2;continue;
            case 3:r7a*=2;continue;
            case 39:ra7*=2;continue;
            case -28:r55*=2;continue;
            case -24:r59*=2;continue;
            case 24:r95*=2;continue;
            case 28:r99*=2;continue;
            case -27:r56*=2;continue;
            case -25:r58*=2;continue;
            case -15:r65*=2;continue;
            case -11:r69*=2;continue;
            case 11:r85*=2;continue;
            case 15:r89*=2;continue;
            case 25:r96*=2;continue;
            case 27:r98*=2;continue;
            case -26:r57*=2;continue;
            case -2:r75*=2;continue;
            case 2:r79*=2;continue;
            case 26:r97*=2;continue;
            case -14:r66*=2;continue;
            case -12:r68*=2;continue;
            case 12:r86*=2;continue;
            case 14:r88*=2;continue;
            case -13:r67*=2;continue;
            case -1:r76*=2;continue;
            case 1:r78*=2;continue;
            case 13:r87*=2;continue;
            case 0:r77*=2;continue;
            default:rc.resign();

            }
        }
        
        int b2 = Clock.getBytecodeNum();
        MapLocation recent = recentLocations[(recentLocationsIndex + 9)%10];
        if(recent == null) recent = me;
        
        int c25 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-5,-2))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-5,-2)))/2);
        int c29 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-5,2))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-5,2)))/2);
        int c52 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-2,-5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-2,-5)))/2);
        int c5c = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-2,5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-2,5)))/2);
        int c92 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(2,-5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(2,-5)))/2);
        int c9c = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(2,5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(2,5)))/2);
        int cc5 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(5,-2))) - Math.sqrt(recent.distanceSquaredTo(me.translate(5,-2)))/2);
        int cc9 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(5,2))) - Math.sqrt(recent.distanceSquaredTo(me.translate(5,2)))/2);
        int c26 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-5,-1))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-5,-1)))/2);
        int c28 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-5,1))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-5,1)))/2);
        int c62 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-1,-5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-1,-5)))/2);
        int c6c = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-1,5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-1,5)))/2);
        int c82 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(1,-5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(1,-5)))/2);
        int c8c = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(1,5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(1,5)))/2);
        int cc6 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(5,-1))) - Math.sqrt(recent.distanceSquaredTo(me.translate(5,-1)))/2);
        int cc8 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(5,1))) - Math.sqrt(recent.distanceSquaredTo(me.translate(5,1)))/2);
        int c27 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-5,0))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-5,0)))/2);
        int c34 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-4,-3))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-4,-3)))/2);
        int c3a = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-4,3))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-4,3)))/2);
        int c43 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-3,-4))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-3,-4)))/2);
        int c4b = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(-3,4))) - Math.sqrt(recent.distanceSquaredTo(me.translate(-3,4)))/2);
        int c72 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(0,-5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(0,-5)))/2);
        int c7c = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(0,5))) - Math.sqrt(recent.distanceSquaredTo(me.translate(0,5)))/2);
        int ca3 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(3,-4))) - Math.sqrt(recent.distanceSquaredTo(me.translate(3,-4)))/2);
        int cab = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(3,4))) - Math.sqrt(recent.distanceSquaredTo(me.translate(3,4)))/2);
        int cb4 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(4,-3))) - Math.sqrt(recent.distanceSquaredTo(me.translate(4,-3)))/2);
        int cba = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(4,3))) - Math.sqrt(recent.distanceSquaredTo(me.translate(4,3)))/2);
        int cc7 = 10 * (int)(Math.sqrt(to.distanceSquaredTo(me.translate(5,0))) - Math.sqrt(recent.distanceSquaredTo(me.translate(5,0)))/2);
        b=c25; if(c26<b) b=c26; if(c34<b) b=c34; int c35=b+r35;
        b=c29; if(c28<b) b=c28; if(c3a<b) b=c3a; int c39=b+r39;
        b=c52; if(c62<b) b=c62; if(c43<b) b=c43; int c53=b+r53;
        b=c5c; if(c6c<b) b=c6c; if(c4b<b) b=c4b; int c5b=b+r5b;
        b=c92; if(c82<b) b=c82; if(ca3<b) b=ca3; int c93=b+r93;
        b=c9c; if(c8c<b) b=c8c; if(cab<b) b=cab; int c9b=b+r9b;
        b=cc5; if(cc6<b) b=cc6; if(cb4<b) b=cb4; int cb5=b+rb5;
        b=cc9; if(cc8<b) b=cc8; if(cba<b) b=cba; int cb9=b+rb9;
        b=c34; if(c43<b) b=c43; if(c35<b) b=c35; if(c53<b) b=c53; int c44=b+r44;
        b=c3a; if(c4b<b) b=c4b; if(c39<b) b=c39; if(c5b<b) b=c5b; int c4a=b+r4a;
        b=ca3; if(cb4<b) b=cb4; if(c93<b) b=c93; if(cb5<b) b=cb5; int ca4=b+ra4;
        b=cab; if(cba<b) b=cba; if(c9b<b) b=c9b; if(cb9<b) b=cb9; int caa=b+raa;
        b=c25; if(c26<b) b=c26; if(c27<b) b=c27; if(c35<b) b=c35; int c36=b+r36;
        b=c29; if(c28<b) b=c28; if(c27<b) b=c27; if(c39<b) b=c39; int c38=b+r38;
        b=c52; if(c62<b) b=c62; if(c72<b) b=c72; if(c53<b) b=c53; int c63=b+r63;
        b=c5c; if(c6c<b) b=c6c; if(c7c<b) b=c7c; if(c5b<b) b=c5b; int c6b=b+r6b;
        b=c92; if(c82<b) b=c82; if(c72<b) b=c72; if(c93<b) b=c93; int c83=b+r83;
        b=c9c; if(c8c<b) b=c8c; if(c7c<b) b=c7c; if(c9b<b) b=c9b; int c8b=b+r8b;
        b=cc5; if(cc6<b) b=cc6; if(cc7<b) b=cc7; if(cb5<b) b=cb5; int cb6=b+rb6;
        b=cc9; if(cc8<b) b=cc8; if(cc7<b) b=cc7; if(cb9<b) b=cb9; int cb8=b+rb8;
        b=c26; if(c28<b) b=c28; if(c27<b) b=c27; if(c36<b) b=c36; if(c38<b) b=c38; int c37=b+r37;
        b=c62; if(c82<b) b=c82; if(c72<b) b=c72; if(c63<b) b=c63; if(c83<b) b=c83; int c73=b+r73;
        b=c6c; if(c8c<b) b=c8c; if(c7c<b) b=c7c; if(c6b<b) b=c6b; if(c8b<b) b=c8b; int c7b=b+r7b;
        b=cc6; if(cc8<b) b=cc8; if(cc7<b) b=cc7; if(cb6<b) b=cb6; if(cb8<b) b=cb8; int cb7=b+rb7;
        b=c34; if(c35<b) b=c35; if(c44<b) b=c44; if(c36<b) b=c36; int c45=b+r45;
        b=c3a; if(c39<b) b=c39; if(c4a<b) b=c4a; if(c38<b) b=c38; int c49=b+r49;
        b=c43; if(c53<b) b=c53; if(c44<b) b=c44; if(c63<b) b=c63; if(c45<b) b=c45; int c54=b+r54;
        b=c4b; if(c5b<b) b=c5b; if(c4a<b) b=c4a; if(c6b<b) b=c6b; if(c49<b) b=c49; int c5a=b+r5a;
        b=ca3; if(c93<b) b=c93; if(ca4<b) b=ca4; if(c83<b) b=c83; int c94=b+r94;
        b=cab; if(c9b<b) b=c9b; if(caa<b) b=caa; if(c8b<b) b=c8b; int c9a=b+r9a;
        b=cb4; if(cb5<b) b=cb5; if(ca4<b) b=ca4; if(cb6<b) b=cb6; if(c94<b) b=c94; int ca5=b+ra5;
        b=cba; if(cb9<b) b=cb9; if(caa<b) b=caa; if(cb8<b) b=cb8; if(c9a<b) b=c9a; int ca9=b+ra9;
        b=c35; if(c36<b) b=c36; if(c37<b) b=c37; if(c45<b) b=c45; int c46=b+r46;
        b=c39; if(c38<b) b=c38; if(c37<b) b=c37; if(c49<b) b=c49; int c48=b+r48;
        b=c53; if(c63<b) b=c63; if(c73<b) b=c73; if(c54<b) b=c54; int c64=b+r64;
        b=c5b; if(c6b<b) b=c6b; if(c7b<b) b=c7b; if(c5a<b) b=c5a; int c6a=b+r6a;
        b=c93; if(c83<b) b=c83; if(c73<b) b=c73; if(c94<b) b=c94; int c84=b+r84;
        b=c9b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c9a<b) b=c9a; int c8a=b+r8a;
        b=cb5; if(cb6<b) b=cb6; if(cb7<b) b=cb7; if(ca5<b) b=ca5; int ca6=b+ra6;
        b=cb9; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca9<b) b=ca9; int ca8=b+ra8;
        b=c36; if(c38<b) b=c38; if(c37<b) b=c37; if(c46<b) b=c46; if(c48<b) b=c48; int c47=b+r47;
        b=c63; if(c83<b) b=c83; if(c73<b) b=c73; if(c64<b) b=c64; if(c84<b) b=c84; int c74=b+r74;
        b=c6b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c6a<b) b=c6a; if(c8a<b) b=c8a; int c7a=b+r7a;
        b=cb6; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca6<b) b=ca6; if(ca8<b) b=ca8; int ca7=b+ra7;
        b=c44; if(c45<b) b=c45; if(c54<b) b=c54; if(c46<b) b=c46; if(c64<b) b=c64; int c55=b+r55;
        b=c4a; if(c49<b) b=c49; if(c5a<b) b=c5a; if(c48<b) b=c48; if(c6a<b) b=c6a; int c59=b+r59;
        b=ca4; if(c94<b) b=c94; if(ca5<b) b=ca5; if(c84<b) b=c84; if(ca6<b) b=ca6; int c95=b+r95;
        b=caa; if(c9a<b) b=c9a; if(ca9<b) b=ca9; if(c8a<b) b=c8a; if(ca8<b) b=ca8; int c99=b+r99;
        b=c45; if(c46<b) b=c46; if(c47<b) b=c47; if(c55<b) b=c55; int c56=b+r56;
        b=c49; if(c48<b) b=c48; if(c47<b) b=c47; if(c59<b) b=c59; int c58=b+r58;
        b=c54; if(c64<b) b=c64; if(c74<b) b=c74; if(c55<b) b=c55; if(c56<b) b=c56; int c65=b+r65;
        b=c5a; if(c6a<b) b=c6a; if(c7a<b) b=c7a; if(c59<b) b=c59; if(c58<b) b=c58; int c69=b+r69;
        b=c94; if(c84<b) b=c84; if(c74<b) b=c74; if(c95<b) b=c95; int c85=b+r85;
        b=c9a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c99<b) b=c99; int c89=b+r89;
        b=ca5; if(ca6<b) b=ca6; if(ca7<b) b=ca7; if(c95<b) b=c95; if(c85<b) b=c85; int c96=b+r96;
        b=ca9; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c99<b) b=c99; if(c89<b) b=c89; int c98=b+r98;
        b=c46; if(c48<b) b=c48; if(c47<b) b=c47; if(c56<b) b=c56; if(c58<b) b=c58; int c57=b+r57;
        b=c64; if(c84<b) b=c84; if(c74<b) b=c74; if(c65<b) b=c65; if(c85<b) b=c85; int c75=b+r75;
        b=c6a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c69<b) b=c69; if(c89<b) b=c89; int c79=b+r79;
        b=ca6; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c96<b) b=c96; if(c98<b) b=c98; int c97=b+r97;
        b=c55; if(c56<b) b=c56; if(c65<b) b=c65; if(c57<b) b=c57; if(c75<b) b=c75; int c66=b+r66;
        b=c59; if(c58<b) b=c58; if(c69<b) b=c69; if(c57<b) b=c57; if(c79<b) b=c79; int c68=b+r68;
        b=c95; if(c85<b) b=c85; if(c96<b) b=c96; if(c75<b) b=c75; if(c97<b) b=c97; int c86=b+r86;
        b=c99; if(c89<b) b=c89; if(c98<b) b=c98; if(c79<b) b=c79; if(c97<b) b=c97; int c88=b+r88;
        b=c56; if(c58<b) b=c58; if(c57<b) b=c57; if(c66<b) b=c66; if(c68<b) b=c68; int c67=b+r67;
        b=c65; if(c85<b) b=c85; if(c75<b) b=c75; if(c66<b) b=c66; if(c86<b) b=c86; if(c67<b) b=c67; int c76=b+r76;
        b=c69; if(c89<b) b=c89; if(c79<b) b=c79; if(c68<b) b=c68; if(c88<b) b=c88; if(c67<b) b=c67; int c78=b+r78;
        b=c96; if(c98<b) b=c98; if(c97<b) b=c97; if(c86<b) b=c86; if(c88<b) b=c88; if(c76<b) b=c76; if(c78<b) b=c78; int c87=b+r87;

        int b3 = Clock.getBytecodeNum();
        /*//round 2
        b=c96; if(c98<b) b=c98; if(c97<b) b=c97; if(c86<b) b=c86; if(c88<b) b=c88; if(c76<b) b=c76; if(c78<b) b=c78; if(c87<b) b=c87; c87=b+r87;
        b=c69; if(c89<b) b=c89; if(c79<b) b=c79; if(c68<b) b=c68; if(c88<b) b=c88; if(c67<b) b=c67; if(c78<b) b=c78; if(c87<b) b=c87; c78=b+r78;
        b=c65; if(c85<b) b=c85; if(c75<b) b=c75; if(c66<b) b=c66; if(c86<b) b=c86; if(c67<b) b=c67; if(c76<b) b=c76; if(c87<b) b=c87; c76=b+r76;
        b=c56; if(c58<b) b=c58; if(c57<b) b=c57; if(c66<b) b=c66; if(c68<b) b=c68; if(c67<b) b=c67; if(c76<b) b=c76; if(c78<b) b=c78; c67=b+r67;
        b=c99; if(c89<b) b=c89; if(c98<b) b=c98; if(c79<b) b=c79; if(c97<b) b=c97; if(c88<b) b=c88; if(c78<b) b=c78; if(c87<b) b=c87; c88=b+r88;
        b=c95; if(c85<b) b=c85; if(c96<b) b=c96; if(c75<b) b=c75; if(c97<b) b=c97; if(c86<b) b=c86; if(c76<b) b=c76; if(c87<b) b=c87; c86=b+r86;
        b=c59; if(c58<b) b=c58; if(c69<b) b=c69; if(c57<b) b=c57; if(c79<b) b=c79; if(c68<b) b=c68; if(c67<b) b=c67; if(c78<b) b=c78; c68=b+r68;
        b=c55; if(c56<b) b=c56; if(c65<b) b=c65; if(c57<b) b=c57; if(c75<b) b=c75; if(c66<b) b=c66; if(c67<b) b=c67; if(c76<b) b=c76; c66=b+r66;
        b=ca6; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c96<b) b=c96; if(c98<b) b=c98; if(c97<b) b=c97; if(c86<b) b=c86; if(c88<b) b=c88; if(c87<b) b=c87; c97=b+r97;
        b=c6a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c69<b) b=c69; if(c89<b) b=c89; if(c79<b) b=c79; if(c68<b) b=c68; if(c88<b) b=c88; if(c78<b) b=c78; c79=b+r79;
        b=c64; if(c84<b) b=c84; if(c74<b) b=c74; if(c65<b) b=c65; if(c85<b) b=c85; if(c75<b) b=c75; if(c66<b) b=c66; if(c86<b) b=c86; if(c76<b) b=c76; c75=b+r75;
        b=c46; if(c48<b) b=c48; if(c47<b) b=c47; if(c56<b) b=c56; if(c58<b) b=c58; if(c57<b) b=c57; if(c66<b) b=c66; if(c68<b) b=c68; if(c67<b) b=c67; c57=b+r57;
        b=ca9; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c99<b) b=c99; if(c89<b) b=c89; if(c98<b) b=c98; if(c97<b) b=c97; if(c88<b) b=c88; if(c87<b) b=c87; c98=b+r98;
        b=ca5; if(ca6<b) b=ca6; if(ca7<b) b=ca7; if(c95<b) b=c95; if(c85<b) b=c85; if(c96<b) b=c96; if(c97<b) b=c97; if(c86<b) b=c86; if(c87<b) b=c87; c96=b+r96;
        b=c9a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c99<b) b=c99; if(c89<b) b=c89; if(c98<b) b=c98; if(c79<b) b=c79; if(c88<b) b=c88; if(c78<b) b=c78; c89=b+r89;
        b=c94; if(c84<b) b=c84; if(c74<b) b=c74; if(c95<b) b=c95; if(c85<b) b=c85; if(c96<b) b=c96; if(c75<b) b=c75; if(c86<b) b=c86; if(c76<b) b=c76; c85=b+r85;
        b=c5a; if(c6a<b) b=c6a; if(c7a<b) b=c7a; if(c59<b) b=c59; if(c58<b) b=c58; if(c69<b) b=c69; if(c79<b) b=c79; if(c68<b) b=c68; if(c78<b) b=c78; c69=b+r69;
        b=c54; if(c64<b) b=c64; if(c74<b) b=c74; if(c55<b) b=c55; if(c56<b) b=c56; if(c65<b) b=c65; if(c75<b) b=c75; if(c66<b) b=c66; if(c76<b) b=c76; c65=b+r65;
        b=c49; if(c48<b) b=c48; if(c47<b) b=c47; if(c59<b) b=c59; if(c58<b) b=c58; if(c69<b) b=c69; if(c57<b) b=c57; if(c68<b) b=c68; if(c67<b) b=c67; c58=b+r58;
        b=c45; if(c46<b) b=c46; if(c47<b) b=c47; if(c55<b) b=c55; if(c56<b) b=c56; if(c65<b) b=c65; if(c57<b) b=c57; if(c66<b) b=c66; if(c67<b) b=c67; c56=b+r56;
        b=caa; if(c9a<b) b=c9a; if(ca9<b) b=ca9; if(c8a<b) b=c8a; if(ca8<b) b=ca8; if(c99<b) b=c99; if(c89<b) b=c89; if(c98<b) b=c98; if(c88<b) b=c88; c99=b+r99;
        b=ca4; if(c94<b) b=c94; if(ca5<b) b=ca5; if(c84<b) b=c84; if(ca6<b) b=ca6; if(c95<b) b=c95; if(c85<b) b=c85; if(c96<b) b=c96; if(c86<b) b=c86; c95=b+r95;
        b=c4a; if(c49<b) b=c49; if(c5a<b) b=c5a; if(c48<b) b=c48; if(c6a<b) b=c6a; if(c59<b) b=c59; if(c58<b) b=c58; if(c69<b) b=c69; if(c68<b) b=c68; c59=b+r59;
        b=c44; if(c45<b) b=c45; if(c54<b) b=c54; if(c46<b) b=c46; if(c64<b) b=c64; if(c55<b) b=c55; if(c56<b) b=c56; if(c65<b) b=c65; if(c66<b) b=c66; c55=b+r55;
        b=cb6; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca6<b) b=ca6; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c96<b) b=c96; if(c98<b) b=c98; if(c97<b) b=c97; ca7=b+ra7;
        b=c6b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c6a<b) b=c6a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c69<b) b=c69; if(c89<b) b=c89; if(c79<b) b=c79; c7a=b+r7a;
        b=c63; if(c83<b) b=c83; if(c73<b) b=c73; if(c64<b) b=c64; if(c84<b) b=c84; if(c74<b) b=c74; if(c65<b) b=c65; if(c85<b) b=c85; if(c75<b) b=c75; c74=b+r74;
        b=c36; if(c38<b) b=c38; if(c37<b) b=c37; if(c46<b) b=c46; if(c48<b) b=c48; if(c47<b) b=c47; if(c56<b) b=c56; if(c58<b) b=c58; if(c57<b) b=c57; c47=b+r47;
        b=cb9; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca9<b) b=ca9; if(ca8<b) b=ca8; if(ca7<b) b=ca7; if(c99<b) b=c99; if(c98<b) b=c98; if(c97<b) b=c97; ca8=b+ra8;
        b=cb5; if(cb6<b) b=cb6; if(cb7<b) b=cb7; if(ca5<b) b=ca5; if(ca6<b) b=ca6; if(ca7<b) b=ca7; if(c95<b) b=c95; if(c96<b) b=c96; if(c97<b) b=c97; ca6=b+ra6;
        b=c9b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c9a<b) b=c9a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; if(c99<b) b=c99; if(c89<b) b=c89; if(c79<b) b=c79; c8a=b+r8a;
        b=c93; if(c83<b) b=c83; if(c73<b) b=c73; if(c94<b) b=c94; if(c84<b) b=c84; if(c74<b) b=c74; if(c95<b) b=c95; if(c85<b) b=c85; if(c75<b) b=c75; c84=b+r84;
        b=c5b; if(c6b<b) b=c6b; if(c7b<b) b=c7b; if(c5a<b) b=c5a; if(c6a<b) b=c6a; if(c7a<b) b=c7a; if(c59<b) b=c59; if(c69<b) b=c69; if(c79<b) b=c79; c6a=b+r6a;
        b=c53; if(c63<b) b=c63; if(c73<b) b=c73; if(c54<b) b=c54; if(c64<b) b=c64; if(c74<b) b=c74; if(c55<b) b=c55; if(c65<b) b=c65; if(c75<b) b=c75; c64=b+r64;
        b=c39; if(c38<b) b=c38; if(c37<b) b=c37; if(c49<b) b=c49; if(c48<b) b=c48; if(c47<b) b=c47; if(c59<b) b=c59; if(c58<b) b=c58; if(c57<b) b=c57; c48=b+r48;
        b=c35; if(c36<b) b=c36; if(c37<b) b=c37; if(c45<b) b=c45; if(c46<b) b=c46; if(c47<b) b=c47; if(c55<b) b=c55; if(c56<b) b=c56; if(c57<b) b=c57; c46=b+r46;
        b=cba; if(cb9<b) b=cb9; if(caa<b) b=caa; if(cb8<b) b=cb8; if(c9a<b) b=c9a; if(ca9<b) b=ca9; if(ca8<b) b=ca8; if(c99<b) b=c99; if(c98<b) b=c98; ca9=b+ra9;
        b=cb4; if(cb5<b) b=cb5; if(ca4<b) b=ca4; if(cb6<b) b=cb6; if(c94<b) b=c94; if(ca5<b) b=ca5; if(ca6<b) b=ca6; if(c95<b) b=c95; if(c96<b) b=c96; ca5=b+ra5;
        b=cab; if(c9b<b) b=c9b; if(caa<b) b=caa; if(c8b<b) b=c8b; if(c9a<b) b=c9a; if(ca9<b) b=ca9; if(c8a<b) b=c8a; if(c99<b) b=c99; if(c89<b) b=c89; c9a=b+r9a;
        b=ca3; if(c93<b) b=c93; if(ca4<b) b=ca4; if(c83<b) b=c83; if(c94<b) b=c94; if(ca5<b) b=ca5; if(c84<b) b=c84; if(c95<b) b=c95; if(c85<b) b=c85; c94=b+r94;
        b=c4b; if(c5b<b) b=c5b; if(c4a<b) b=c4a; if(c6b<b) b=c6b; if(c49<b) b=c49; if(c5a<b) b=c5a; if(c6a<b) b=c6a; if(c59<b) b=c59; if(c69<b) b=c69; c5a=b+r5a;
        b=c43; if(c53<b) b=c53; if(c44<b) b=c44; if(c63<b) b=c63; if(c45<b) b=c45; if(c54<b) b=c54; if(c64<b) b=c64; if(c55<b) b=c55; if(c65<b) b=c65; c54=b+r54;
        b=c3a; if(c39<b) b=c39; if(c4a<b) b=c4a; if(c38<b) b=c38; if(c49<b) b=c49; if(c5a<b) b=c5a; if(c48<b) b=c48; if(c59<b) b=c59; if(c58<b) b=c58; c49=b+r49;
        b=c34; if(c35<b) b=c35; if(c44<b) b=c44; if(c36<b) b=c36; if(c45<b) b=c45; if(c54<b) b=c54; if(c46<b) b=c46; if(c55<b) b=c55; if(c56<b) b=c56; c45=b+r45;
        b=cc6; if(cc8<b) b=cc8; if(cc7<b) b=cc7; if(cb6<b) b=cb6; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca6<b) b=ca6; if(ca8<b) b=ca8; if(ca7<b) b=ca7; cb7=b+rb7;
        b=c6c; if(c8c<b) b=c8c; if(c7c<b) b=c7c; if(c6b<b) b=c6b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c6a<b) b=c6a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; c7b=b+r7b;
        b=c62; if(c82<b) b=c82; if(c72<b) b=c72; if(c63<b) b=c63; if(c83<b) b=c83; if(c73<b) b=c73; if(c64<b) b=c64; if(c84<b) b=c84; if(c74<b) b=c74; c73=b+r73;
        b=c26; if(c28<b) b=c28; if(c27<b) b=c27; if(c36<b) b=c36; if(c38<b) b=c38; if(c37<b) b=c37; if(c46<b) b=c46; if(c48<b) b=c48; if(c47<b) b=c47; c37=b+r37;
        b=cc9; if(cc8<b) b=cc8; if(cc7<b) b=cc7; if(cb9<b) b=cb9; if(cb8<b) b=cb8; if(cb7<b) b=cb7; if(ca9<b) b=ca9; if(ca8<b) b=ca8; if(ca7<b) b=ca7; cb8=b+rb8;
        b=cc5; if(cc6<b) b=cc6; if(cc7<b) b=cc7; if(cb5<b) b=cb5; if(cb6<b) b=cb6; if(cb7<b) b=cb7; if(ca5<b) b=ca5; if(ca6<b) b=ca6; if(ca7<b) b=ca7; cb6=b+rb6;
        b=c9c; if(c8c<b) b=c8c; if(c7c<b) b=c7c; if(c9b<b) b=c9b; if(c8b<b) b=c8b; if(c7b<b) b=c7b; if(c9a<b) b=c9a; if(c8a<b) b=c8a; if(c7a<b) b=c7a; c8b=b+r8b;
        b=c92; if(c82<b) b=c82; if(c72<b) b=c72; if(c93<b) b=c93; if(c83<b) b=c83; if(c73<b) b=c73; if(c94<b) b=c94; if(c84<b) b=c84; if(c74<b) b=c74; c83=b+r83;
        b=c5c; if(c6c<b) b=c6c; if(c7c<b) b=c7c; if(c5b<b) b=c5b; if(c6b<b) b=c6b; if(c7b<b) b=c7b; if(c5a<b) b=c5a; if(c6a<b) b=c6a; if(c7a<b) b=c7a; c6b=b+r6b;
        b=c52; if(c62<b) b=c62; if(c72<b) b=c72; if(c53<b) b=c53; if(c63<b) b=c63; if(c73<b) b=c73; if(c54<b) b=c54; if(c64<b) b=c64; if(c74<b) b=c74; c63=b+r63;
        b=c29; if(c28<b) b=c28; if(c27<b) b=c27; if(c39<b) b=c39; if(c38<b) b=c38; if(c37<b) b=c37; if(c49<b) b=c49; if(c48<b) b=c48; if(c47<b) b=c47; c38=b+r38;
        b=c25; if(c26<b) b=c26; if(c27<b) b=c27; if(c35<b) b=c35; if(c36<b) b=c36; if(c37<b) b=c37; if(c45<b) b=c45; if(c46<b) b=c46; if(c47<b) b=c47; c36=b+r36;
        b=cab; if(cba<b) b=cba; if(c9b<b) b=c9b; if(cb9<b) b=cb9; if(caa<b) b=caa; if(c9a<b) b=c9a; if(ca9<b) b=ca9; if(c99<b) b=c99; caa=b+raa;
        b=ca3; if(cb4<b) b=cb4; if(c93<b) b=c93; if(cb5<b) b=cb5; if(ca4<b) b=ca4; if(c94<b) b=c94; if(ca5<b) b=ca5; if(c95<b) b=c95; ca4=b+ra4;
        b=c3a; if(c4b<b) b=c4b; if(c39<b) b=c39; if(c5b<b) b=c5b; if(c4a<b) b=c4a; if(c49<b) b=c49; if(c5a<b) b=c5a; if(c59<b) b=c59; c4a=b+r4a;
        b=c34; if(c43<b) b=c43; if(c35<b) b=c35; if(c53<b) b=c53; if(c44<b) b=c44; if(c45<b) b=c45; if(c54<b) b=c54; if(c55<b) b=c55; c44=b+r44;
        b=cc9; if(cc8<b) b=cc8; if(cba<b) b=cba; if(cb9<b) b=cb9; if(caa<b) b=caa; if(cb8<b) b=cb8; if(ca9<b) b=ca9; if(ca8<b) b=ca8; cb9=b+rb9;
        b=cc5; if(cc6<b) b=cc6; if(cb4<b) b=cb4; if(cb5<b) b=cb5; if(ca4<b) b=ca4; if(cb6<b) b=cb6; if(ca5<b) b=ca5; if(ca6<b) b=ca6; cb5=b+rb5;
        b=c9c; if(c8c<b) b=c8c; if(cab<b) b=cab; if(c9b<b) b=c9b; if(caa<b) b=caa; if(c8b<b) b=c8b; if(c9a<b) b=c9a; if(c8a<b) b=c8a; c9b=b+r9b;
        b=c92; if(c82<b) b=c82; if(ca3<b) b=ca3; if(c93<b) b=c93; if(ca4<b) b=ca4; if(c83<b) b=c83; if(c94<b) b=c94; if(c84<b) b=c84; c93=b+r93;
        b=c5c; if(c6c<b) b=c6c; if(c4b<b) b=c4b; if(c5b<b) b=c5b; if(c4a<b) b=c4a; if(c6b<b) b=c6b; if(c5a<b) b=c5a; if(c6a<b) b=c6a; c5b=b+r5b;
        b=c52; if(c62<b) b=c62; if(c43<b) b=c43; if(c53<b) b=c53; if(c44<b) b=c44; if(c63<b) b=c63; if(c54<b) b=c54; if(c64<b) b=c64; c53=b+r53;
        b=c29; if(c28<b) b=c28; if(c3a<b) b=c3a; if(c39<b) b=c39; if(c4a<b) b=c4a; if(c38<b) b=c38; if(c49<b) b=c49; if(c48<b) b=c48; c39=b+r39;
        b=c25; if(c26<b) b=c26; if(c34<b) b=c34; if(c35<b) b=c35; if(c44<b) b=c44; if(c36<b) b=c36; if(c45<b) b=c45; if(c46<b) b=c46; c35=b+r35;
        */
        int b4 = Clock.getBytecodeNum();
        
        b=c78; Direction bestD = Direction.NORTH;
        if(c88<b) {b=c88; bestD = Direction.NORTHEAST;}
        if(c87<b) {b=c87; bestD = Direction.EAST;}
        if(c86<b) {b=c86; bestD = Direction.SOUTHEAST;}
        if(c76<b) {b=c76; bestD = Direction.SOUTH;}
        if(c66<b) {b=c66; bestD = Direction.SOUTHWEST;}
        if(c67<b) {b=c67; bestD = Direction.WEST;}
        if(c68<b) {b=c68; bestD = Direction.NORTHWEST;}
        if(rc.canMove(bestD)) rc.move(bestD);
        rc.setIndicatorLine(rc.getLocation(), to, 255, 255, 0);

        int b5 = Clock.getBytecodeNum();
        
        rc.setIndicatorString("setup "+(b1-b0)+" robots "+(b2-b1)+" i "+(b3-b2) + " i2 "+(b4-b3)+" move "+(b5-b4));
        
        /*
        MapLocation current = me;
        outer:
        while(true) {
            b = 999999;
            MapLocation bestStep = null;
            for(Direction d : Robot.directions) {
                MapLocation m = current.add(d);
                int c;
                switch((m.x - myx)*13 + (m.y - myy)) {
                case -67:c=c25;break;
                case -63:c=c29;break;
                case -31:c=c52;break;
                case -21:c=c5c;break;
                case 21:c=c92;break;
                case 31:c=c9c;break;
                case 63:c=cc5;break;
                case 67:c=cc9;break;
                case -66:c=c26;break;
                case -64:c=c28;break;
                case -18:c=c62;break;
                case -8:c=c6c;break;
                case 8:c=c82;break;
                case 18:c=c8c;break;
                case 64:c=cc6;break;
                case 66:c=cc8;break;
                case -65:c=c27;break;
                case -55:c=c34;break;
                case -49:c=c3a;break;
                case -43:c=c43;break;
                case -35:c=c4b;break;
                case -5:c=c72;break;
                case 5:c=c7c;break;
                case 35:c=ca3;break;
                case 43:c=cab;break;
                case 49:c=cb4;break;
                case 55:c=cba;break;
                case 65:c=cc7;break;
                case -54:c=c35;break;
                case -50:c=c39;break;
                case -30:c=c53;break;
                case -22:c=c5b;break;
                case 22:c=c93;break;
                case 30:c=c9b;break;
                case 50:c=cb5;break;
                case 54:c=cb9;break;
                case -42:c=c44;break;
                case -36:c=c4a;break;
                case 36:c=ca4;break;
                case 42:c=caa;break;
                case -53:c=c36;break;
                case -51:c=c38;break;
                case -17:c=c63;break;
                case -9:c=c6b;break;
                case 9:c=c83;break;
                case 17:c=c8b;break;
                case 51:c=cb6;break;
                case 53:c=cb8;break;
                case -52:c=c37;break;
                case -4:c=c73;break;
                case 4:c=c7b;break;
                case 52:c=cb7;break;
                case -41:c=c45;break;
                case -37:c=c49;break;
                case -29:c=c54;break;
                case -23:c=c5a;break;
                case 23:c=c94;break;
                case 29:c=c9a;break;
                case 37:c=ca5;break;
                case 41:c=ca9;break;
                case -40:c=c46;break;
                case -38:c=c48;break;
                case -16:c=c64;break;
                case -10:c=c6a;break;
                case 10:c=c84;break;
                case 16:c=c8a;break;
                case 38:c=ca6;break;
                case 40:c=ca8;break;
                case -39:c=c47;break;
                case -3:c=c74;break;
                case 3:c=c7a;break;
                case 39:c=ca7;break;
                case -28:c=c55;break;
                case -24:c=c59;break;
                case 24:c=c95;break;
                case 28:c=c99;break;
                case -27:c=c56;break;
                case -25:c=c58;break;
                case -15:c=c65;break;
                case -11:c=c69;break;
                case 11:c=c85;break;
                case 15:c=c89;break;
                case 25:c=c96;break;
                case 27:c=c98;break;
                case -26:c=c57;break;
                case -2:c=c75;break;
                case 2:c=c79;break;
                case 26:c=c97;break;
                case -14:c=c66;break;
                case -12:c=c68;break;
                case 12:c=c86;break;
                case 14:c=c88;break;
                case -13:c=c67;break;
                case -1:c=c76;break;
                case 1:c=c78;break;
                case 13:c=c87;break;
                case 0:c=999999;break;
                default:break outer;

                }
                if(c < b) {
                    b = c;
                    bestStep = m;
                }
            }
            if(bestStep == null) rc.resign();
            rc.setIndicatorLine(current, bestStep, 0, 255, 255);
            current = bestStep;
        }
        */
    }
}
