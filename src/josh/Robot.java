package josh;

import java.util.Random;

import battlecode.common.*;

public abstract class Robot {
    public static final boolean DEBUG=true;
    static final Random rng = new Random(6147);
    RobotController rc;
    Robot(RobotController r) {
        rc = r;
    }
    void run() {
        while(true) {
            try {
                turn();
            } catch(Exception e) {
                e.printStackTrace();
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
                suitability[i] /= rc.senseRubble(l);
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
}
