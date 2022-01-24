package matir;

import battlecode.common.*;

import java.util.Random;

import static battlecode.common.RobotType.*;
import static java.lang.Math.*;

public abstract class Robot {
    public static final int SEED = 7328;
    public static final int MAX_LEAD = 1000; // trigger to start building watchtowers
    public static final int MAX_HEALS = 3;
    public static final double HEALTH_FACTOR = 0.2;

    public static final int INDEX_MY_HQ = 0; //4 ints for friendly HQ locations
    public static final int INDEX_ENEMY_HQ = 4; //4 ints for known enemy HQ locs
    public static final int INDEX_LIVE_MINERS = 8;
    public static final int INDEX_INCOME = 9;
    public static final int INDEX_ENEMY_UNIT_LOCATION = 10;
    /*
    Locations of enemy soldiers, sages, and watchtowers
    Each chunk is 16 = 5 + 1 + 1 + 9 bits ssbbbtelllllllll
    lllllllll = location = 20x + y, counting 3x3 blocks as units
    e = era = (round >> 4) & 1
    t = totality of information seen = 0 if partial, 1 if total
    ssbbb = estimated strength of units in the block
        ss = scaling
        bbb = bits = n (0-7)
        strength =  0 + 0.50 * n if ss = 0
                    4 + 0.75 * n if ss = 1
                   10 + 1.00 * n if ss = 2
                   18 + 2.00 * n if ss = 3
        strength maxed out at 32 for ssbbb = 0x1FF
    SPECIAL:
    Unused chunks contain 0xFFFF
     */
    public static final int NUM_ENEMY_UNIT_CHUNKS = 9; // can be decreased
    public static final int INDEX_ENEMY_ETC_LOCATION =
            INDEX_ENEMY_UNIT_LOCATION + NUM_ENEMY_UNIT_CHUNKS;
    /*
    Locations of enemy miners and labs.
    Each int contains two bytes, and each byte is elllllll
    e = era
    lllllll = 10x + y, counting 6x6 blocks as regions
    Unused bytes contain 0xFF
     */
    public static final int NUM_ENEMY_ETC_CHUNKS = 3; // can be modified
    public static final int INDEX_MY_UNIT_LOCATIONS =
            INDEX_ENEMY_ETC_LOCATION + NUM_ENEMY_ETC_CHUNKS;
    /*
    Locations of our units
    Each chunk is 16 = 1 + 1 + 2 + 3 + 1 + 1 + 7 bits abrrsssqflllllllll
    lllllllll = location = 10x + y, counting 6x6 blocks as regions
    f = frontier (0 if not fighting, 1 if fighting)
    q = who is stronger (0 if we are stronger, 1 if they are)
    rr = current rubble (0-10/11-25/26-50/51-100)
    sss = difference in strength:
        sss = 0: [0, 0.5)
        sss = 1: [0.5, 1.2)
        sss = 2: [1.2, 2.0)
        sss = 3: [2.0, 3.0)
        sss = 4: [3.0, 4.5)
        sss = 5: [4.5, 7)
        sss = 6: [7, 10)
        sss = 7: [10, inf)
    a = relative advance rubble slope (0 if lower rubble on hold, 1 if lower on advance)
    b = relative retreat rubble slope (0 if lower rubble on hold, 1 if lower on retreat)
    SPECIAL:
    Unused chunks contain 0xFFFF
     */
    public static final int NUM_MY_UNIT_CHUNKS = 18;
    public static final int INDEX_LEAD_DEPOSITS =
            INDEX_MY_UNIT_LOCATIONS + NUM_MY_UNIT_CHUNKS;
    /*
    Each region is 10 = 3 + 7 bits xyzllllllll
        lllllll = location = 20x + y, counting 6x6 blocks as regions
        xyz = least power of 2 greater than lead amount in block
    */
    public static final int NUM_LEAD_DEPOSIT_INTS = 10;
    public static final int NUM_LEAD_DEPOSITS = 16;
    public static final int NUM_LEAD_DEPOSIT_BITS = 10;
    public static final int INDEX_EXPLORED_CHUNKS = 50; //4 ints (64 bits, one for each sections of map, divide map into 8 sections each way)
    public static final int INDEX_ARCHON_LOC = 54;
    public static final int INDEX_HQ_SPENDING = 55; //one bit for is alive, two bits for round num mod 4, remainder for total lead spent.
    public static final int INDEX_HQBAD = 59;
    public static final int INDEX_MISC = 63; // one bit things
    public static final int BIT_RELOCATE = 0;
    public static final int BIT_SYMMETRY = 1;
    public static final int BIT_LAB = 2;
    public static final int BIT_HEALING = 3;

    MapLocation myLoc;
    MapLocation[] corners;

    MapLocation hqLoc = null; // stores (original) location of HQ that spawned this
    int[] savedEnemyUnitLocs = new int[NUM_ENEMY_UNIT_CHUNKS];
    int[] savedEnemyEtcLocs = new int[NUM_ENEMY_ETC_CHUNKS * 2];
    int[] savedMyLocs = new int[NUM_MY_UNIT_CHUNKS];
    int[] savedLead = new int[NUM_LEAD_DEPOSITS];

    abstract static class Weightage {
        abstract double weight(Direction d);
    }

    class RubbleWeight extends Weightage {

        @Override
        double weight(Direction d) {
            try {
                if (rc.canSenseLocation(myLoc.add(d)))
                    return 1000 / (10.0 + rc.senseRubble(myLoc.add(d)));
                else
                    return 0;
            } catch (GameActionException e) {
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
            if (newDist > curDist) return 1;
            return (10 * (sqrt(curDist) - sqrt(newDist)) + 1) * (1 + rubbleWeight.weight(d));
        }
    }

    /*
     * intention is for each enemy seen within the last 20 rounds is in here
     * but only put distinct entries if they are more than 4 tiles apart.
     * when anyone sees an enemy check if it would be a new entry. if so add it with the round number.
     */

    public static final boolean DEBUG = false;
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
        myLoc = rc.getLocation();
    }

    MapLocation[] recentLocations = new MapLocation[10];
    int recentLocationsIndex = 0;
    int lastMoveTurn = 0;

    void run() {
        // initialize hqLoc to be adjacent HQ
        if (hqLoc == null) {
            RobotInfo[] nearbyRobots = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo robot : nearbyRobots) if (robot.type == RobotType.ARCHON) hqLoc = robot.location;
        }
        while (true) {
            try {
                myLoc = rc.getLocation();
                turn();
                if(rc.getRoundNum() % 7 == 3) saveInfo();
                updateInfo();

                if (!rc.getLocation().equals(recentLocations[recentLocationsIndex])) {
                    recentLocationsIndex = (recentLocationsIndex + 1) % 10;
                    recentLocations[recentLocationsIndex] = rc.getLocation();
                    lastMoveTurn = rc.getRoundNum();
                }
                if (DEBUG) {
                    MapLocation last = rc.getLocation();
                    for (int i = recentLocationsIndex + 9; i > recentLocationsIndex; i--) {
                        if (recentLocations[i % 10] != null) {
                            MapLocation next = recentLocations[i % 10];
                            // rc.setIndicatorLine(last, next, 255, 0, 0);
                            last = next;
                        }
                    }
                }
            } catch (GameActionException e) {
                rc.setIndicatorString(e.getStackTrace()[2].toString());
            } catch (RuntimeException e) {
                try {
                    rc.setIndicatorString(e.getStackTrace()[0].toString());
                } catch (Exception f) {
                    throw e;
                }
                //rc.setIndicatorString(e.toString());
            }
            Clock.yield();
        }
    }

    double getHeat(MapLocation loc) {
        double myHeat = 0, enemyHeat = 0;
        for(int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            enemyHeat += chunkToPower(savedEnemyUnitLocs[i]) /
                    (1 + loc.distanceSquaredTo(chunkToloc(
                            savedEnemyUnitLocs[i])));
        }
        myHeat = -enemyHeat;
        for(int i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
            if(((savedMyLocs[i] >> 7) & 1) == 1)
            myHeat += chunkToPowerDiff(savedMyLocs[i]) /
                    (1 + loc.distanceSquaredTo(chunkToloc(
                            savedMyLocs[i])));
        }
        double ratio = max(myHeat, 0.01) / max(enemyHeat, 0.01);
        return log(ratio)/log(2);
    }

    double chunkToPowerDiff(int chunk) {
        int q = 1 - (((chunk >> 8) & 1) * 2);
        int s = (chunk >> 9) & 7;
    /*
        sss = 0: [0, 0.5)
        sss = 1: [0.5, 1.2)
        sss = 2: [1.2, 2.0)
        sss = 3: [2.0, 3.0)
        sss = 4: [3.0, 4.5)
        sss = 5: [4.5, 7)
        sss = 6: [7, 10)
        sss = 7: [10, inf)
        */
        switch(s) {
            case 0: return q * 0.25;
            case 1: return q * 0.85;
            case 2: return q * 1.6;
            case 3: return q * 2.5;
            case 4: return q * 3.75;
            case 5: return q * 5.75;
            case 6: return q * 8.5;
            case 7: return q * 15;
        }

        return 0;
    }

    int readMisc(int bit) throws GameActionException {
        return (rc.readSharedArray(INDEX_MISC) >> bit & 1);
    }

    void writeMisc(int bit, int out) throws GameActionException {
        rc.writeSharedArray(INDEX_MISC,
                (rc.readSharedArray(INDEX_MISC)
                        & (0xFFFF - (1 << bit))) | (out << bit));
    }

    void updateInfo() throws GameActionException {
        updateEnemyHQs();
        if(isAttacker(rc.getType()) &&
                rng.nextInt() < 0.5) {
            updateMyLocation();
        }
        updateEnemyLocations();
        updateLead();
    }

    void saveInfo() throws GameActionException {
        for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            savedEnemyUnitLocs[i] = rc.readSharedArray(i + INDEX_ENEMY_UNIT_LOCATION);
        }

        for (int i = 0; i < NUM_ENEMY_ETC_CHUNKS; i++) {
            int x = rc.readSharedArray(i + INDEX_ENEMY_ETC_LOCATION);
            savedEnemyEtcLocs[2*i] = x & 0xFF;
            savedEnemyEtcLocs[2*i+1] = (x >> 8) & 0xFF;
        }

        for (int i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
            savedMyLocs[i] = rc.readSharedArray(i + INDEX_MY_UNIT_LOCATIONS);
        }

        for (int i = 0; i < NUM_LEAD_DEPOSITS; i++) {
            savedLead[i] = getDeposit(i);
        }
    }

    void updateLead() throws GameActionException {
        if(Clock.getBytecodesLeft() < 2000) return;

        int[] leadInts = new int[NUM_LEAD_DEPOSIT_INTS];
        int[] leadChunks = new int[NUM_LEAD_DEPOSITS];

        int l = locToEtc(myLoc);
        MapLocation[] nearby = rc.senseNearbyLocationsWithLead(
                rc.getType().visionRadiusSquared);

        int amt = 0;
        for (MapLocation loc : nearby) {
            amt += rc.senseLead(loc);
        }

        for (int i = 0; i < NUM_LEAD_DEPOSIT_INTS; i++) {
            leadInts[i] = rc.readSharedArray(i + INDEX_LEAD_DEPOSITS);
        }

        int low = 8, lowi = -1, empi = -1;

        for (int i = 0; i < NUM_LEAD_DEPOSITS; i++) {
            int j = (i * NUM_LEAD_DEPOSIT_INTS) / NUM_LEAD_DEPOSITS;
            int k = (i * NUM_LEAD_DEPOSIT_INTS) - (j * NUM_LEAD_DEPOSITS);
            int dump = leadInts[j];
            if (j < NUM_LEAD_DEPOSIT_INTS - 1)
                dump |= leadInts[j + 1] << 16;
            leadChunks[i] = (dump >> k) & ((1 << NUM_LEAD_DEPOSIT_BITS) - 1);
            if(leadChunks[i] == 0x3FF) return;
            if ((leadChunks[i] & 0x7F) == l) return;
            if ((leadChunks[i] >> 7) < low) {
                low = leadChunks[i] >> 7;
                lowi = i;
            }
            if (leadChunks[i] == 0x3FF) {
                empi = i;
            }
        }

        if (empi >= 0) lowi = empi;
        if (pow(2, low + 0.5) > amt) return;

        int lead, out;
        if (amt == 0) {
            lead = 0;
        } else {
            lead = (int) (log(amt) / log(2.001)) + 1;
        }
        out = l | (lead << 7);

        int j = (lowi * NUM_LEAD_DEPOSIT_INTS) / NUM_LEAD_DEPOSITS;
        int k = (lowi * NUM_LEAD_DEPOSIT_INTS) - (j * NUM_LEAD_DEPOSITS);
        int m = k + NUM_LEAD_DEPOSIT_BITS - 16;
        rc.writeSharedArray(j, (leadInts[j] & ((1 << k) - 1)) |
                ((out & ((1 << (NUM_LEAD_DEPOSIT_BITS-m)) - 1)) << k));
        if (m > 0) {
            rc.writeSharedArray(j + 1,
                    (leadInts[j+1] & ((1 << 16) - (1 << m))) |
                            (out >> (NUM_LEAD_DEPOSIT_BITS - m)));
        }

        //rc.setIndicatorDot(chunkToloc(l), 0, 30 * lead, 0);
    }

    int getDeposit(int i) throws GameActionException {
        int j = (i * NUM_LEAD_DEPOSIT_INTS) / NUM_LEAD_DEPOSITS;
        int k = (i * NUM_LEAD_DEPOSIT_INTS) - (j * NUM_LEAD_DEPOSITS);
        int dump = savedLead[j];

        if (j < NUM_LEAD_DEPOSIT_INTS - 1)
            dump |= savedLead[j+1] << 16;

        return (dump >> k) & ((1 << NUM_LEAD_DEPOSIT_BITS) - 1);
    }

    MapLocation chunkToloc(int l) {
        int z = l & 0x7F;
        return new MapLocation((z / 10) * 6 + 3, (z % 10) * 6 + 3);
    }

    public abstract void turn() throws GameActionException;

    /**
     * Array containing all the possible movement directions.
     */
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
        if (rc.canMove(moveDir))
            rc.move(moveDir);
    }

    public Direction bestMove(Direction d) throws GameActionException {
        Direction[] dd = {d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight()};
        double[] suitability = {1, .5, .5, .1, .1};
        for (int i = 0; i < 5; i++) {
            MapLocation l = rc.getLocation().add(dd[i]);
            if (rc.onTheMap(l))
                suitability[i] /= 10 + rc.senseRubble(l);

        }
        double best = 0;
        Direction bestD = Direction.CENTER;
        for (int i = 0; i < 5; i++) {
            if (suitability[i] > best && rc.canMove(dd[i])) {
                best = suitability[i];
                bestD = dd[i];
            }
        }
        return bestD;
    }

    int frustration = 10;
    MapLocation lastMoveTowardTarget;

    public void moveToward(MapLocation to) throws GameActionException {
        nav(to);
        return;
    }

    public Direction randDirByWeight(Direction[] dirs, Weightage wt) {

        double[] cwt = new double[dirs.length + 1];
        cwt[0] = 0;

        for (int i = 1; i <= dirs.length; ++i) {
            cwt[i] = cwt[i - 1] + wt.weight(dirs[i - 1]);
        }

        if (cwt[dirs.length] == 0) return null;

        for (int i = 1; i <= dirs.length; ++i) {
            cwt[i] /= cwt[dirs.length];
        }

        double r = rng.nextDouble();
        int i = 0;
        while (cwt[i] < r) i++;

        return dirs[i - 1];
    }

    public int chunkToInt(MapLocation l) {
        return 20 * (l.x / 3) + (l.y / 3);
    }

    public MapLocation intToChunk(int t) {
        int z = t & 0x1FF;

        return new MapLocation((z / 20) * 3 + 1,
                (z % 20) * 3 + 1);
    }

    public int locToInt(MapLocation l) {
        return (l.x << 7) | l.y | 0x4000;
    }

    public MapLocation intToLoc(int x) {
        return new MapLocation((x >> 7) & 0x7f, x & 0x7f);
    }

    /*
     * using the locations of our HQs and map symmetry, get a random possible enemy HQ location weighted by distance away.
     */
    public MapLocation getRandomPossibleEnemyHQ() throws GameActionException {
        MapLocation[] possibleEnemyHQs = new MapLocation[12];
        for (int i = 0; i < 4 && rc.readSharedArray(i + Robot.INDEX_MY_HQ) > 0; i++) {
            MapLocation l = intToLoc(rc.readSharedArray(i + Robot.INDEX_MY_HQ));
            possibleEnemyHQs[i * 3] = new MapLocation(rc.getMapWidth() - 1 - l.x, l.y);
            possibleEnemyHQs[i * 3 + 1] = new MapLocation(l.x, rc.getMapHeight() - 1 - l.y);
            possibleEnemyHQs[i * 3 + 2] = new MapLocation(rc.getMapWidth() - 1 - l.x, rc.getMapHeight() - 1 - l.y);
        }
        int totalWeight = 0;
        for (int i = 0; i < 12; i++) {
            if (possibleEnemyHQs[i] == null) continue;
            totalWeight += possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        if (totalWeight == 0) return null;
        int r = rng.nextInt(totalWeight);
        rc.setIndicatorString("w=" + totalWeight + " r=" + r);
        int i;
        for (i = 0; r >= 0; i++) {
            r -= possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        return possibleEnemyHQs[i - 1];
    }

    public MapLocation getRandomKnownEnemyHQ() throws GameActionException {
        MapLocation[] possibleEnemyHQs = new MapLocation[4];
        for (int i = 0; i < 4; i++) {
            if (rc.readSharedArray(i + Robot.INDEX_ENEMY_HQ) > 0)
                possibleEnemyHQs[i] = intToLoc(rc.readSharedArray(i + Robot.INDEX_ENEMY_HQ));
        }
        int totalWeight = 0;
        for (int i = 0; i < 4; i++) {
            if (possibleEnemyHQs[i] == null) continue;
            totalWeight += 1000000 / possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        if (totalWeight == 0) return null;
        int r = rng.nextInt(totalWeight);
        int i;
        for (i = 0; r >= 0; i++) {
            if (possibleEnemyHQs[i] == null) continue;
            r -= 1000000 / possibleEnemyHQs[i].distanceSquaredTo(rc.getLocation());
        }
        return possibleEnemyHQs[i - 1];
    }

    public void updateEnemyHQs() throws GameActionException {
        MapLocation[] needsUpdating = new MapLocation[4];
        MapLocation[] newEnemyHQs = new MapLocation[4];
        int newIndex = 0;
        for (int i = 0; i < 4; i++) {
            MapLocation l = intToLoc(rc.readSharedArray(i + Robot.INDEX_ENEMY_HQ));
            if (rc.canSenseLocation(l))
                needsUpdating[i] = l;
        }
        for (RobotInfo r : rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent())) {
            if (r.type == RobotType.ARCHON) {
                int i;
                for (i = 0; i < 4; i++) {
                    if (r.location.equals(needsUpdating[i])) {//this accounts for an existing one
                        needsUpdating[i] = null;
                        break;
                    }
                }
                if (i == 4) { //this is a new enemy HQ
                    newEnemyHQs[newIndex++] = r.location;
                }
            }
        }
        while (newIndex > 0) {
            newIndex--;
            for (int i = 0; i < 4; i++) {
                if (rc.readSharedArray(i + Robot.INDEX_ENEMY_HQ) > 0 && needsUpdating[i] == null)
                    continue;
                rc.writeSharedArray(i + Robot.INDEX_ENEMY_HQ, locToInt(newEnemyHQs[newIndex]));
                needsUpdating[i] = null;
                break;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (needsUpdating[i] != null)
                rc.writeSharedArray(i + Robot.INDEX_ENEMY_HQ, 0);
        }
    }

    void removeOldEnemyUnitLocations() throws GameActionException {
        if ((rc.getRoundNum() & 0xF) != 0) return;
        int e = ((rc.getRoundNum() >> 4) & 1);

        for (int i = INDEX_ENEMY_UNIT_LOCATION;
             i < INDEX_ENEMY_UNIT_LOCATION + NUM_ENEMY_UNIT_CHUNKS; i++) {
            int x = rc.readSharedArray(i);
            if (x == 0xFFFF) continue;
            if (e == ((x >> 9) & 1) || rc.getRoundNum() < 2)
                rc.writeSharedArray(i, 0xFFFF);
        }

        for (int i = INDEX_ENEMY_ETC_LOCATION;
             i < INDEX_ENEMY_ETC_LOCATION + NUM_ENEMY_ETC_CHUNKS; i++) {
            int x = rc.readSharedArray(i);
            int x1 = x & 0xFF, x2 = x & 0xFF00;
            int v1 = x1, v2 = x2;
            if (e == ((x1 >> 7) & 1) || rc.getRoundNum() < 2) {
                v1 = 0xFF;
            }
            if (e == ((x2 >> 15) & 1) || rc.getRoundNum() < 2) {
                v2 = 0xFF00;
            }
            if (v1 != x1 || v2 != x2) {
                rc.writeSharedArray(i, v1 | v2);
            }
        }
    }

    void removeFriendlyUnitLocations() throws GameActionException {
        if (rc.getRoundNum() < 2) {
            for (int i = INDEX_MY_UNIT_LOCATIONS;
                 i < INDEX_MY_UNIT_LOCATIONS + NUM_MY_UNIT_CHUNKS; i++) {
                rc.writeSharedArray(i, 0xFFFF);
            }

            return;
        }

        for (int i = INDEX_MY_UNIT_LOCATIONS;
             i < INDEX_MY_UNIT_LOCATIONS + NUM_MY_UNIT_CHUNKS; i++) {
            int x = rc.readSharedArray(i);
            if (x == 0xFFFF || x == 0x00FF)
                rc.writeSharedArray(i, 0xFFFF);
            else
                rc.writeSharedArray(i, 0x00FF);
        }
    }

    void putEtc(int i, int val) throws GameActionException {
        int ins, old;
        if (i % 2 == 1) {
            ins = val << 8;
            old = rc.readSharedArray(i / 2) & 0x00FF;
        } else {
            ins = val;
            old = rc.readSharedArray(i / 2) & 0xFF00;
        }
        rc.writeSharedArray(i / 2, ins | old);
    }

    int getEtc(int i) throws GameActionException {
        int x = rc.readSharedArray(i / 2);
        if (i % 2 == 1) return x >> 8;
        return x & 0xFF;
    }

    void updateEnemyLocations() throws GameActionException {

        RobotInfo[] rbs = rc.senseNearbyRobots(
                rc.getType().visionRadiusSquared, rc.getTeam().opponent());

        if (Clock.getBytecodesLeft() < 1800 + rbs.length * 225) return;

        int[] enemyUnitChunks = new int[NUM_ENEMY_UNIT_CHUNKS];
        int[] newEnemyUnitChunks = new int[NUM_ENEMY_UNIT_CHUNKS];
        boolean[] freeEtcChunk = new boolean[NUM_ENEMY_ETC_CHUNKS * 2];
        boolean freeChunks = false;
        for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            //enemySoldiers[i] = Robot.intToChunk(rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION+i));
            int x = rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION + i);
            enemyUnitChunks[i] = x;
            newEnemyUnitChunks[i] = 0xFFFF;
        }

        for (int i = 0; i < NUM_ENEMY_ETC_CHUNKS; i++) {
            int x = rc.readSharedArray(INDEX_ENEMY_ETC_LOCATION + i);
            freeEtcChunk[i * 2] = (x & 0x00FF) == 0x00FF;
            freeEtcChunk[i * 2 + 1] = (x & 0xFF00) == 0xFF00;
            freeChunks |= freeChunks || freeEtcChunk[i * 2] || freeEtcChunk[i * 2 + 1];
        }

        int e = (rc.getRoundNum() >> 4) & 1;

        for (RobotInfo r : rbs) {
            if(Clock.getBytecodesLeft() < 200) return;

            if (!isAttacker(r)) {
                if (!freeChunks) continue;

                boolean succ = false;
                for (int i = 0; i <= NUM_ENEMY_ETC_CHUNKS * 2; i++) {
                    if (freeEtcChunk[i]) {
                        putEtc(i, locToEtc(r.location) | (e << 7));
                        succ = true;
                        break;
                    }
                }
                if (!succ) freeChunks = false;

                continue;
            }

            double p = 0;
            int x = chunkToInt(r.location);

            MapLocation center = intToChunk(x);
            MapLocation farthest = center.translate(
                    myLoc.x > center.x ? -1 : 1,
                    myLoc.y > center.y ? -1 : 1
            );
            int t = myLoc.distanceSquaredTo(farthest) <=
                    rc.getType().visionRadiusSquared ? 1 : 0;
            int findi = -1;

            for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
                if ((newEnemyUnitChunks[i] & 0x1FF) == x ||
                        (enemyUnitChunks[i] & 0x1FF) == x) {
                    findi = i;
                    break;
                }
            }

            if (findi >= 0) {
                if (t == 0)
                    if (enemyUnitChunks[findi] != 0xFFFF)
                        if (((enemyUnitChunks[findi] >> 10) & 1) == 1)
                            continue;

                p = newEnemyUnitChunks[findi] == 0xFFFF ?
                        0 : chunkToPower(newEnemyUnitChunks[findi]);
            } else {
                for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
                    if (newEnemyUnitChunks[i] == 0xFFFF) {
                        findi = i;
                    }
                }
            }

            p += power(r);
            int s, n;
            if (p < 3.75) {
                s = 0;
                n = (int) ((p + 0.25) / 0.5);
                if (n == 0) n = 0x800;
            } else if (p < 9.625) {
                s = 1;
                n = (int) ((p - 4 + 0.375) / 0.75);
            } else if (p < 17.5) {
                s = 2;
                n = (int) (p - 10 + 0.5);
            } else {
                s = 3;
                n = (int) ((p - 18 + 1) / 2);
                if (n > 7) n = 7;
            }

            newEnemyUnitChunks[findi] = x | (e << 9) | (t << 10) | (n << 11) | (s << 14);
        }

        for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            if(Clock.getBytecodesLeft() < 200) return;
            if (newEnemyUnitChunks[i] != 0xFFFF) {
                rc.writeSharedArray(INDEX_ENEMY_UNIT_LOCATION + i, newEnemyUnitChunks[i]);
            }
        }
    }

    int locToEtc(MapLocation loc) {
        return (loc.x / 6) * 10 + (loc.y / 6);
    }

    double chunkToPower(int chunk) {
        int s = chunk >> 14;
        int n = (chunk >> 11) & 7;

        switch (s) {
            case 0:
                return 0 + 0.50 * n;
            case 1:
                return 4 + 0.75 * n;
            case 2:
                return 10 + 1.00 * n;
            case 3:
                return 18 + 2.00 * n;
        }

        return 0;
    }

    double scale(RobotInfo r) {
        if (!isAttacker(r)) return 0;

        double s = 0;

        switch (r.type) {
            case SAGE:
                s = 2.5;
                break;
            case SOLDIER:
                s = 1.0;
                break;
            case WATCHTOWER:
                s = 1.333 * r.level;
                break;
            case BUILDER:
                s = 0.66;
                break;
            case ARCHON:
                s = 0.66;
                break;
        }

        return s;
    }

    double power(RobotInfo r) {
        if (!isAttacker(r)) return 0;
        double s = scale(r);

        return s * (sqrt(r.getHealth()) + 1) /
                (sqrt(SOLDIER.getMaxHealth(1)) + 1);
    }

    MapLocation getNearestEnemySoldierChunk() throws GameActionException {
        MapLocation nearest = null;

        for (int i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            int x1 = rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION + i);
            if (x1 == 0xFFFF) continue;
            MapLocation x = intToChunk(x1);
            if (nearest == null || rc.getLocation().distanceSquaredTo(x) < rc.getLocation().distanceSquaredTo(nearest)) {
                nearest = x;
            }
        }

        return nearest;
    }
    MapLocation getNearestEnemyChunk() throws GameActionException {
        MapLocation nearest = getNearestEnemySoldierChunk();

        for(int i = 0; i < NUM_ENEMY_ETC_CHUNKS; i++) {
            int z = rc.readSharedArray(INDEX_ENEMY_ETC_LOCATION + i);
            int x1 = z & 0xFF;
            int x2 = (z >> 8) & 0xFF;
            MapLocation l1 = chunkToloc(x1);
            MapLocation l2 = chunkToloc(x2);
            if(!(x1 == 0xFF)) {
                if (nearest == null || rc.getLocation().distanceSquaredTo(l1) <
                        rc.getLocation().distanceSquaredTo(nearest)) {
                    nearest = l1;
                }
            }
            if(!(x2 == 0xFF)) {
                if (nearest == null || rc.getLocation().distanceSquaredTo(l2) <
                        rc.getLocation().distanceSquaredTo(nearest)) {
                    nearest = l2;
                }
            }
        }
        return nearest;
    }

    MapLocation getNearestUnexploredChunk(MapLocation l) throws GameActionException {
        int x0 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 0);
        int x1 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 1);
        int x2 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 2);
        int x3 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 3);
        int mh = rc.getMapHeight(), mw = rc.getMapWidth();
        MapLocation m;
        MapLocation best = null;
        int bestD = 9999;
        if ((x0 & 0x1) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x2) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x4) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x8) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x10) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x20) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x40) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x80) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 1 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x100) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x200) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x400) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x800) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x1000) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x2000) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x4000) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x0 & 0x8000) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 3 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x1) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x2) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x4) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x8) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x10) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x20) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x40) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x80) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 5 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x100) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x200) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x400) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x800) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x1000) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x2000) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x4000) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x1 & 0x8000) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 7 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x1) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x2) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x4) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x8) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x10) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x20) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x40) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x80) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 9 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x100) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x200) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x400) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x800) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x1000) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x2000) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x4000) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x2 & 0x8000) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 11 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x1) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x2) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x4) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x8) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x10) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x20) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x40) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x80) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 13 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x100) == 0 && (m = new MapLocation(mw * 1 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x200) == 0 && (m = new MapLocation(mw * 3 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x400) == 0 && (m = new MapLocation(mw * 5 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x800) == 0 && (m = new MapLocation(mw * 7 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x1000) == 0 && (m = new MapLocation(mw * 9 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x2000) == 0 && (m = new MapLocation(mw * 11 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x4000) == 0 && (m = new MapLocation(mw * 13 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }
        if ((x3 & 0x8000) == 0 && (m = new MapLocation(mw * 15 / 16, mh * 15 / 16)).distanceSquaredTo(l) < bestD) {
            best = m;
            bestD = m.distanceSquaredTo(l);
        }

        return best;
    }

    /*
    void displayUnexploredChunks() throws GameActionException {
        int x0 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 0);
        int x1 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 1);
        int x2 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 2);
        int x3 = rc.readSharedArray(INDEX_EXPLORED_CHUNKS + 3);
        int mh = rc.getMapHeight(), mw = rc.getMapWidth();
        if ((x0 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 1 / 16), 0, 0, 255);
        if ((x0 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x0 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 3 / 16), 0, 0, 255);
        if ((x1 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 5 / 16), 0, 0, 255);
        if ((x1 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x1 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 7 / 16), 0, 0, 255);
        if ((x2 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 9 / 16), 0, 0, 255);
        if ((x2 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x2 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 11 / 16), 0, 0, 255);
        if ((x3 & 0x1) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x2) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x4) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x8) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x10) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x20) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x40) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x80) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 13 / 16), 0, 0, 255);
        if ((x3 & 0x100) == 0) rc.setIndicatorDot(new MapLocation(mw * 1 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x200) == 0) rc.setIndicatorDot(new MapLocation(mw * 3 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x400) == 0) rc.setIndicatorDot(new MapLocation(mw * 5 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x800) == 0) rc.setIndicatorDot(new MapLocation(mw * 7 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x1000) == 0) rc.setIndicatorDot(new MapLocation(mw * 9 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x2000) == 0) rc.setIndicatorDot(new MapLocation(mw * 11 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x4000) == 0) rc.setIndicatorDot(new MapLocation(mw * 13 / 16, mh * 15 / 16), 0, 0, 255);
        if ((x3 & 0x8000) == 0) rc.setIndicatorDot(new MapLocation(mw * 15 / 16, mh * 15 / 16), 0, 0, 255);
    }*/

    void writeUnexploredChunk() throws GameActionException {
        MapLocation l = rc.getLocation();
        int k = l.x * 8 / rc.getMapWidth() + (l.y * 8 / rc.getMapHeight()) * 8;
        //rc.setIndicatorString(""+k);
        int i = k / 16, j = k % 16;
        //rc.setIndicatorString(Integer.toBinaryString(rc.readSharedArray(INDEX_EXPLORED_CHUNKS+0))+" "+Integer.toBinaryString(rc.readSharedArray(INDEX_EXPLORED_CHUNKS+1))
        //+" "+rc.readSharedArray(INDEX_EXPLORED_CHUNKS+2)+" "+rc.readSharedArray(INDEX_EXPLORED_CHUNKS+3)+" "+i+" "+j);
        if ((rc.readSharedArray(INDEX_EXPLORED_CHUNKS + i) & (1 << j)) == 0)
            rc.writeSharedArray(INDEX_EXPLORED_CHUNKS + i, rc.readSharedArray(INDEX_EXPLORED_CHUNKS + i) | (1 << j));
    }

    void clearUnexploredChunks() throws GameActionException {
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS + 0, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS + 1, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS + 2, 0);
        rc.writeSharedArray(Robot.INDEX_EXPLORED_CHUNKS + 3, 0);
    }

    double computeStrength(RobotInfo[] robots) throws GameActionException {
        double strength = 0;

        for (RobotInfo r : robots) {
            strength += power(r) / (10.0 + rc.senseRubble(r.location));
        }

        return (int) strength;
    }

    int myIndex = -1;

    void updateMyLocation() throws GameActionException {
        if (Clock.getBytecodesLeft() < 3200) return;
        if(hqLoc == null) return;

        int[] chunk = new int[NUM_MY_UNIT_CHUNKS];
        int l = locToEtc(myLoc);

        if (myIndex >= 0) {
            if ((chunk[myIndex] & 0x7F) != l) {
                myIndex = -1;
            }
        }

        if (myIndex == -1) {
            for (int i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
                chunk[i] = rc.readSharedArray(i + INDEX_MY_UNIT_LOCATIONS);
                if ((chunk[i] & 0x7F) == l) return;
            }

            for (int i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
                if (chunk[i] == 0xFFFF) {
                    myIndex = i;
                    break;
                }
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());

        int f = enemies.length > 0 ? 1 : 0;

        if (myIndex == -1 && f == 0) return;

        if (myIndex == -1) {
            for (int i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
                if ((chunk[i] & 0x0080) == 0) {
                    myIndex = i;
                    break;
                }
            }
        }

        if (myIndex == -1) return;

        RobotInfo[] friends = rc.senseNearbyRobots(
                rc.getType().visionRadiusSquared, rc.getTeam());

        if(Clock.getBytecodesLeft() <
                1200 + (friends.length + enemies.length) * 120) return;

        int n = 0;

        double dx = hqLoc.x - myLoc.x;
        double dy = hqLoc.y - myLoc.y;
        double d = sqrt(dx * dx + dy * dy);
        dx /= d;
        dy /= d;
        double rad = sqrt(rc.getType().visionRadiusSquared / 2.0);

        double rub = rubble(myLoc);
        int a = rubble(myLoc.translate(-(int) (dx * rad), -(int) (dy * rad))) > rub ? 1 : 0;
        int b = rubble(myLoc.translate((int) (dx * rad), (int) (dy * rad))) > rub ? 1 : 0;
        double st = (int) ((computeStrength(enemies) - computeStrength(friends))
                * (10 + rub));
        int s;
        if (st < 0.5) {
            s = 0;
        } else if (st < 1.2) {
            s = 1;
        } else if (st < 2) {
            s = 2;
        } else if (st < 3) {
            s = 3;
        } else if (st < 4.5) {
            s = 4;
        } else if (st < 7) {
            s = 5;
        } else if (st < 12) {
            s = 6;
        } else {
            s = 7;
        }
        int q = st > 0 ? 1 : 0;
        int r;
        if (rub <= 10) {
            r = 0;
        } else if (rub <= 24) {
            r = 1;
        } else if (rub <= 50) {
            r = 2;
        } else {
            r = 3;
        }

        rc.writeSharedArray(myIndex, l | (f << 7) | (q << 8) | (s << 9) |
                (r << 12) | (b << 14) | (a << 15));

        rc.setIndicatorDot(chunkToloc(l), 100 + s*20, 100 + s*20, 0);
    }

    double rubble(MapLocation loc) throws GameActionException {
        int n = 0;
        double r = 1;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation l = loc.translate(dx, dy);
                if (!rc.canSenseLocation(l)) continue;
                r *= rc.senseRubble(l);
                n++;
            }
        }

        return pow(r, 1.0 / n);
    }

    int numNonFrustratedMoves = 0;
    MapLocation lastNavEndpoint = null;
    MapLocation lastNavUse = null;
    int turnsFailedToMove = 0;
    int c25;
    int c29;
    int c52;
    int c5c;
    int c92;
    int c9c;
    int cc5;
    int cc9;
    int c26;
    int c28;
    int c62;
    int c6c;
    int c82;
    int c8c;
    int cc6;
    int cc8;
    int c27;
    int c34;
    int c3a;
    int c43;
    int c4b;
    int c72;
    int c7c;
    int ca3;
    int cab;
    int cb4;
    int cba;
    int cc7;

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
        if (!me.equals(lastNavUse)) frustration = 1;
        int myx = me.x, myy = me.y;
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();

        boolean onMapX7 = true;
        boolean onMapY7 = true;
        boolean onMapX2 = myx + -5 >= 0;
        boolean onMapY2 = myy + -5 >= 0;
        boolean onMapX3 = myx + -4 >= 0;
        boolean onMapY3 = myy + -4 >= 0;
        boolean onMapX4 = myx + -3 >= 0;
        boolean onMapY4 = myy + -3 >= 0;
        boolean onMapX5 = myx + -2 >= 0;
        boolean onMapY5 = myy + -2 >= 0;
        boolean onMapX6 = myx + -1 >= 0;
        boolean onMapY6 = myy + -1 >= 0;
        boolean onMapX8 = myx + 1 < mapWidth;
        boolean onMapY8 = myy + 1 < mapHeight;
        boolean onMapX9 = myx + 2 < mapWidth;
        boolean onMapY9 = myy + 2 < mapHeight;
        boolean onMapXa = myx + 3 < mapWidth;
        boolean onMapYa = myy + 3 < mapHeight;
        boolean onMapXb = myx + 4 < mapWidth;
        boolean onMapYb = myy + 4 < mapHeight;
        boolean onMapXc = myx + 5 < mapWidth;
        boolean onMapYc = myy + 5 < mapHeight;
        int r35 = (onMapX3 && onMapY5) ? rc.senseRubble(me.translate(-4, -2)) / frustration + 10 : 1000;
        int r39 = (onMapX3 && onMapY9) ? rc.senseRubble(me.translate(-4, 2)) / frustration + 10 : 1000;
        int r53 = (onMapX5 && onMapY3) ? rc.senseRubble(me.translate(-2, -4)) / frustration + 10 : 1000;
        int r5b = (onMapX5 && onMapYb) ? rc.senseRubble(me.translate(-2, 4)) / frustration + 10 : 1000;
        int r93 = (onMapX9 && onMapY3) ? rc.senseRubble(me.translate(2, -4)) / frustration + 10 : 1000;
        int r9b = (onMapX9 && onMapYb) ? rc.senseRubble(me.translate(2, 4)) / frustration + 10 : 1000;
        int rb5 = (onMapXb && onMapY5) ? rc.senseRubble(me.translate(4, -2)) / frustration + 10 : 1000;
        int rb9 = (onMapXb && onMapY9) ? rc.senseRubble(me.translate(4, 2)) / frustration + 10 : 1000;
        int r44 = (onMapX4 && onMapY4) ? rc.senseRubble(me.translate(-3, -3)) / frustration + 10 : 1000;
        int r4a = (onMapX4 && onMapYa) ? rc.senseRubble(me.translate(-3, 3)) / frustration + 10 : 1000;
        int ra4 = (onMapXa && onMapY4) ? rc.senseRubble(me.translate(3, -3)) / frustration + 10 : 1000;
        int raa = (onMapXa && onMapYa) ? rc.senseRubble(me.translate(3, 3)) / frustration + 10 : 1000;
        int r36 = (onMapX3 && onMapY6) ? rc.senseRubble(me.translate(-4, -1)) / frustration + 10 : 1000;
        int r38 = (onMapX3 && onMapY8) ? rc.senseRubble(me.translate(-4, 1)) / frustration + 10 : 1000;
        int r63 = (onMapX6 && onMapY3) ? rc.senseRubble(me.translate(-1, -4)) / frustration + 10 : 1000;
        int r6b = (onMapX6 && onMapYb) ? rc.senseRubble(me.translate(-1, 4)) / frustration + 10 : 1000;
        int r83 = (onMapX8 && onMapY3) ? rc.senseRubble(me.translate(1, -4)) / frustration + 10 : 1000;
        int r8b = (onMapX8 && onMapYb) ? rc.senseRubble(me.translate(1, 4)) / frustration + 10 : 1000;
        int rb6 = (onMapXb && onMapY6) ? rc.senseRubble(me.translate(4, -1)) / frustration + 10 : 1000;
        int rb8 = (onMapXb && onMapY8) ? rc.senseRubble(me.translate(4, 1)) / frustration + 10 : 1000;
        int r37 = (onMapX3 && onMapY7) ? rc.senseRubble(me.translate(-4, 0)) / frustration + 10 : 1000;
        int r73 = (onMapX7 && onMapY3) ? rc.senseRubble(me.translate(0, -4)) / frustration + 10 : 1000;
        int r7b = (onMapX7 && onMapYb) ? rc.senseRubble(me.translate(0, 4)) / frustration + 10 : 1000;
        int rb7 = (onMapXb && onMapY7) ? rc.senseRubble(me.translate(4, 0)) / frustration + 10 : 1000;
        int r45 = (onMapX4 && onMapY5) ? rc.senseRubble(me.translate(-3, -2)) / frustration + 10 : 1000;
        int r49 = (onMapX4 && onMapY9) ? rc.senseRubble(me.translate(-3, 2)) / frustration + 10 : 1000;
        int r54 = (onMapX5 && onMapY4) ? rc.senseRubble(me.translate(-2, -3)) / frustration + 10 : 1000;
        int r5a = (onMapX5 && onMapYa) ? rc.senseRubble(me.translate(-2, 3)) / frustration + 10 : 1000;
        int r94 = (onMapX9 && onMapY4) ? rc.senseRubble(me.translate(2, -3)) / frustration + 10 : 1000;
        int r9a = (onMapX9 && onMapYa) ? rc.senseRubble(me.translate(2, 3)) / frustration + 10 : 1000;
        int ra5 = (onMapXa && onMapY5) ? rc.senseRubble(me.translate(3, -2)) / frustration + 10 : 1000;
        int ra9 = (onMapXa && onMapY9) ? rc.senseRubble(me.translate(3, 2)) / frustration + 10 : 1000;
        int r46 = (onMapX4 && onMapY6) ? rc.senseRubble(me.translate(-3, -1)) / frustration + 10 : 1000;
        int r48 = (onMapX4 && onMapY8) ? rc.senseRubble(me.translate(-3, 1)) / frustration + 10 : 1000;
        int r64 = (onMapX6 && onMapY4) ? rc.senseRubble(me.translate(-1, -3)) / frustration + 10 : 1000;
        int r6a = (onMapX6 && onMapYa) ? rc.senseRubble(me.translate(-1, 3)) / frustration + 10 : 1000;
        int r84 = (onMapX8 && onMapY4) ? rc.senseRubble(me.translate(1, -3)) / frustration + 10 : 1000;
        int r8a = (onMapX8 && onMapYa) ? rc.senseRubble(me.translate(1, 3)) / frustration + 10 : 1000;
        int ra6 = (onMapXa && onMapY6) ? rc.senseRubble(me.translate(3, -1)) / frustration + 10 : 1000;
        int ra8 = (onMapXa && onMapY8) ? rc.senseRubble(me.translate(3, 1)) / frustration + 10 : 1000;
        int r47 = (onMapX4 && onMapY7) ? rc.senseRubble(me.translate(-3, 0)) / frustration + 10 : 1000;
        int r74 = (onMapX7 && onMapY4) ? rc.senseRubble(me.translate(0, -3)) / frustration + 10 : 1000;
        int r7a = (onMapX7 && onMapYa) ? rc.senseRubble(me.translate(0, 3)) / frustration + 10 : 1000;
        int ra7 = (onMapXa && onMapY7) ? rc.senseRubble(me.translate(3, 0)) / frustration + 10 : 1000;
        int r55 = (onMapX5 && onMapY5) ? rc.senseRubble(me.translate(-2, -2)) / frustration + 10 : 1000;
        int r59 = (onMapX5 && onMapY9) ? rc.senseRubble(me.translate(-2, 2)) / frustration + 10 : 1000;
        int r95 = (onMapX9 && onMapY5) ? rc.senseRubble(me.translate(2, -2)) / frustration + 10 : 1000;
        int r99 = (onMapX9 && onMapY9) ? rc.senseRubble(me.translate(2, 2)) / frustration + 10 : 1000;
        int r56 = (onMapX5 && onMapY6) ? rc.senseRubble(me.translate(-2, -1)) / frustration + 10 : 1000;
        int r58 = (onMapX5 && onMapY8) ? rc.senseRubble(me.translate(-2, 1)) / frustration + 10 : 1000;
        int r65 = (onMapX6 && onMapY5) ? rc.senseRubble(me.translate(-1, -2)) / frustration + 10 : 1000;
        int r69 = (onMapX6 && onMapY9) ? rc.senseRubble(me.translate(-1, 2)) / frustration + 10 : 1000;
        int r85 = (onMapX8 && onMapY5) ? rc.senseRubble(me.translate(1, -2)) / frustration + 10 : 1000;
        int r89 = (onMapX8 && onMapY9) ? rc.senseRubble(me.translate(1, 2)) / frustration + 10 : 1000;
        int r96 = (onMapX9 && onMapY6) ? rc.senseRubble(me.translate(2, -1)) / frustration + 10 : 1000;
        int r98 = (onMapX9 && onMapY8) ? rc.senseRubble(me.translate(2, 1)) / frustration + 10 : 1000;
        int r57 = (onMapX5 && onMapY7) ? rc.senseRubble(me.translate(-2, 0)) / frustration + 10 : 1000;
        int r75 = (onMapX7 && onMapY5) ? rc.senseRubble(me.translate(0, -2)) / frustration + 10 : 1000;
        int r79 = (onMapX7 && onMapY9) ? rc.senseRubble(me.translate(0, 2)) / frustration + 10 : 1000;
        int r97 = (onMapX9 && onMapY7) ? rc.senseRubble(me.translate(2, 0)) / frustration + 10 : 1000;
        int r66 = (onMapX6 && onMapY6) ? rc.senseRubble(me.translate(-1, -1)) / frustration + 10 : 1000;
        int r68 = (onMapX6 && onMapY8) ? rc.senseRubble(me.translate(-1, 1)) / frustration + 10 : 1000;
        int r86 = (onMapX8 && onMapY6) ? rc.senseRubble(me.translate(1, -1)) / frustration + 10 : 1000;
        int r88 = (onMapX8 && onMapY8) ? rc.senseRubble(me.translate(1, 1)) / frustration + 10 : 1000;
        int r67 = (onMapX6 && onMapY7) ? rc.senseRubble(me.translate(-1, 0)) / frustration + 10 : 1000;
        int r76 = (onMapX7 && onMapY6) ? rc.senseRubble(me.translate(0, -1)) / frustration + 10 : 1000;
        int r78 = (onMapX7 && onMapY8) ? rc.senseRubble(me.translate(0, 1)) / frustration + 10 : 1000;
        int r87 = (onMapX8 && onMapY7) ? rc.senseRubble(me.translate(1, 0)) / frustration + 10 : 1000;
        int r77 = (onMapX7 && onMapY7) ? rc.senseRubble(me.translate(0, 0)) / frustration + 10 : 1000;

        //bytecode so far = 1553;
        int b1 = Clock.getBytecodeNum();

        for (RobotInfo r : rc.senseNearbyRobots(20)) {
            switch ((r.location.x - myx) * 13 + (r.location.y - myy)) {
                case -54:
                    r35 *= 1 + frustration;
                    continue;
                case -50:
                    r39 *= 1 + frustration;
                    continue;
                case -30:
                    r53 *= 1 + frustration;
                    continue;
                case -22:
                    r5b *= 1 + frustration;
                    continue;
                case 22:
                    r93 *= 1 + frustration;
                    continue;
                case 30:
                    r9b *= 1 + frustration;
                    continue;
                case 50:
                    rb5 *= 1 + frustration;
                    continue;
                case 54:
                    rb9 *= 1 + frustration;
                    continue;
                case -42:
                    r44 *= 1 + frustration;
                    continue;
                case -36:
                    r4a *= 1 + frustration;
                    continue;
                case 36:
                    ra4 *= 1 + frustration;
                    continue;
                case 42:
                    raa *= 1 + frustration;
                    continue;
                case -53:
                    r36 *= 1 + frustration;
                    continue;
                case -51:
                    r38 *= 1 + frustration;
                    continue;
                case -17:
                    r63 *= 1 + frustration;
                    continue;
                case -9:
                    r6b *= 1 + frustration;
                    continue;
                case 9:
                    r83 *= 1 + frustration;
                    continue;
                case 17:
                    r8b *= 1 + frustration;
                    continue;
                case 51:
                    rb6 *= 1 + frustration;
                    continue;
                case 53:
                    rb8 *= 1 + frustration;
                    continue;
                case -52:
                    r37 *= 1 + frustration;
                    continue;
                case -4:
                    r73 *= 1 + frustration;
                    continue;
                case 4:
                    r7b *= 1 + frustration;
                    continue;
                case 52:
                    rb7 *= 1 + frustration;
                    continue;
                case -41:
                    r45 *= 1 + frustration;
                    continue;
                case -37:
                    r49 *= 1 + frustration;
                    continue;
                case -29:
                    r54 *= 1 + frustration;
                    continue;
                case -23:
                    r5a *= 1 + frustration;
                    continue;
                case 23:
                    r94 *= 1 + frustration;
                    continue;
                case 29:
                    r9a *= 1 + frustration;
                    continue;
                case 37:
                    ra5 *= 1 + frustration;
                    continue;
                case 41:
                    ra9 *= 1 + frustration;
                    continue;
                case -40:
                    r46 *= 1 + frustration;
                    continue;
                case -38:
                    r48 *= 1 + frustration;
                    continue;
                case -16:
                    r64 *= 1 + frustration;
                    continue;
                case -10:
                    r6a *= 1 + frustration;
                    continue;
                case 10:
                    r84 *= 1 + frustration;
                    continue;
                case 16:
                    r8a *= 1 + frustration;
                    continue;
                case 38:
                    ra6 *= 1 + frustration;
                    continue;
                case 40:
                    ra8 *= 1 + frustration;
                    continue;
                case -39:
                    r47 *= 1 + frustration;
                    continue;
                case -3:
                    r74 *= 1 + frustration;
                    continue;
                case 3:
                    r7a *= 1 + frustration;
                    continue;
                case 39:
                    ra7 *= 1 + frustration;
                    continue;
                case -28:
                    r55 *= 1 + frustration;
                    continue;
                case -24:
                    r59 *= 1 + frustration;
                    continue;
                case 24:
                    r95 *= 1 + frustration;
                    continue;
                case 28:
                    r99 *= 1 + frustration;
                    continue;
                case -27:
                    r56 *= 1 + frustration;
                    continue;
                case -25:
                    r58 *= 1 + frustration;
                    continue;
                case -15:
                    r65 *= 1 + frustration;
                    continue;
                case -11:
                    r69 *= 1 + frustration;
                    continue;
                case 11:
                    r85 *= 1 + frustration;
                    continue;
                case 15:
                    r89 *= 1 + frustration;
                    continue;
                case 25:
                    r96 *= 1 + frustration;
                    continue;
                case 27:
                    r98 *= 1 + frustration;
                    continue;
                case -26:
                    r57 *= 1 + frustration;
                    continue;
                case -2:
                    r75 *= 1 + frustration;
                    continue;
                case 2:
                    r79 *= 1 + frustration;
                    continue;
                case 26:
                    r97 *= 1 + frustration;
                    continue;
                case -14:
                    r66 *= 1 + frustration;
                    continue;
                case -12:
                    r68 *= 1 + frustration;
                    continue;
                case 12:
                    r86 *= 1 + frustration;
                    continue;
                case 14:
                    r88 *= 1 + frustration;
                    continue;
                case -13:
                    r67 *= 1 + frustration;
                    continue;
                case -1:
                    r76 *= 1 + frustration;
                    continue;
                case 1:
                    r78 *= 1 + frustration;
                    continue;
                case 13:
                    r87 *= 1 + frustration;
                    continue;
                case 0:
                    r77 *= 1 + frustration;
                    continue;
                default:
                    rc.resign();

            }
        }

        int b2 = Clock.getBytecodeNum();
        MapLocation recent = recentLocations[(recentLocationsIndex + 9) % 10];
        if (recent == null) recent = me;

        //navRecent
        /*
        MapLocation l=recent;
        if(l!=null){switch((l.x-myx)*13+(l.y-myy)){
        case -14:r66+=400;r67+=400;r76+=400;break;
        case -12:r68+=400;r67+=400;r78+=400;break;
        case 12:r86+=400;r76+=400;r87+=400;break;
        case 14:r88+=400;r78+=400;r87+=400;break;
        case -13:r66+=400;r68+=400;r67+=400;r77+=400;break;
        case -1:r66+=400;r86+=400;r76+=400;r77+=400;break;
        case 1:r68+=400;r88+=400;r78+=400;r77+=400;break;
        case 13:r86+=400;r88+=400;r87+=400;r77+=400;break;
        default:break;}}
        */
        //nav3

        if (to != null) {
            c25 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-5, -2))));
            c29 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-5, 2))));
            c52 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-2, -5))));
            c5c = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-2, 5))));
            c92 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(2, -5))));
            c9c = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(2, 5))));
            cc5 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(5, -2))));
            cc9 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(5, 2))));
            c26 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-5, -1))));
            c28 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-5, 1))));
            c62 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-1, -5))));
            c6c = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-1, 5))));
            c82 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(1, -5))));
            c8c = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(1, 5))));
            cc6 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(5, -1))));
            cc8 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(5, 1))));
            c27 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-5, 0))));
            c34 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-4, -3))));
            c3a = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-4, 3))));
            c43 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-3, -4))));
            c4b = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(-3, 4))));
            c72 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(0, -5))));
            c7c = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(0, 5))));
            ca3 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(3, -4))));
            cab = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(3, 4))));
            cb4 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(4, -3))));
            cba = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(4, 3))));
            cc7 = (int) (10 * Math.sqrt(to.distanceSquaredTo(me.translate(5, 0))));

        }
        if (lastNavEndpoint != null && to != null) {
            Direction lastD = null;
            Direction currentD = me.directionTo(to);
            if (lastMoveTowardTarget != null)
                lastD = me.directionTo(lastMoveTowardTarget);
            if (!(lastMoveTowardTarget == null || lastD == currentD || lastD == currentD.rotateLeft() || lastD == currentD.rotateRight())) {
                //this is a new movement target, so we reset the frustration.
                //frustration = 1;
            }
            //rc.setIndicatorDot(lastNavEndpoint, 0, 255, 255);
        }

        b = c25;
        if (c26 < b) b = c26;
        if (c34 < b) b = c34;
        int c35 = b + r35;
        b = c29;
        if (c28 < b) b = c28;
        if (c3a < b) b = c3a;
        int c39 = b + r39;
        b = c52;
        if (c62 < b) b = c62;
        if (c43 < b) b = c43;
        int c53 = b + r53;
        b = c5c;
        if (c6c < b) b = c6c;
        if (c4b < b) b = c4b;
        int c5b = b + r5b;
        b = c92;
        if (c82 < b) b = c82;
        if (ca3 < b) b = ca3;
        int c93 = b + r93;
        b = c9c;
        if (c8c < b) b = c8c;
        if (cab < b) b = cab;
        int c9b = b + r9b;
        b = cc5;
        if (cc6 < b) b = cc6;
        if (cb4 < b) b = cb4;
        int cb5 = b + rb5;
        b = cc9;
        if (cc8 < b) b = cc8;
        if (cba < b) b = cba;
        int cb9 = b + rb9;
        b = c34;
        if (c43 < b) b = c43;
        if (c35 < b) b = c35;
        if (c53 < b) b = c53;
        int c44 = b + r44;
        b = c3a;
        if (c4b < b) b = c4b;
        if (c39 < b) b = c39;
        if (c5b < b) b = c5b;
        int c4a = b + r4a;
        b = ca3;
        if (cb4 < b) b = cb4;
        if (c93 < b) b = c93;
        if (cb5 < b) b = cb5;
        int ca4 = b + ra4;
        b = cab;
        if (cba < b) b = cba;
        if (c9b < b) b = c9b;
        if (cb9 < b) b = cb9;
        int caa = b + raa;
        b = c25;
        if (c26 < b) b = c26;
        if (c27 < b) b = c27;
        if (c35 < b) b = c35;
        int c36 = b + r36;
        b = c29;
        if (c28 < b) b = c28;
        if (c27 < b) b = c27;
        if (c39 < b) b = c39;
        int c38 = b + r38;
        b = c52;
        if (c62 < b) b = c62;
        if (c72 < b) b = c72;
        if (c53 < b) b = c53;
        int c63 = b + r63;
        b = c5c;
        if (c6c < b) b = c6c;
        if (c7c < b) b = c7c;
        if (c5b < b) b = c5b;
        int c6b = b + r6b;
        b = c92;
        if (c82 < b) b = c82;
        if (c72 < b) b = c72;
        if (c93 < b) b = c93;
        int c83 = b + r83;
        b = c9c;
        if (c8c < b) b = c8c;
        if (c7c < b) b = c7c;
        if (c9b < b) b = c9b;
        int c8b = b + r8b;
        b = cc5;
        if (cc6 < b) b = cc6;
        if (cc7 < b) b = cc7;
        if (cb5 < b) b = cb5;
        int cb6 = b + rb6;
        b = cc9;
        if (cc8 < b) b = cc8;
        if (cc7 < b) b = cc7;
        if (cb9 < b) b = cb9;
        int cb8 = b + rb8;
        b = c26;
        if (c28 < b) b = c28;
        if (c27 < b) b = c27;
        if (c36 < b) b = c36;
        if (c38 < b) b = c38;
        int c37 = b + r37;
        b = c62;
        if (c82 < b) b = c82;
        if (c72 < b) b = c72;
        if (c63 < b) b = c63;
        if (c83 < b) b = c83;
        int c73 = b + r73;
        b = c6c;
        if (c8c < b) b = c8c;
        if (c7c < b) b = c7c;
        if (c6b < b) b = c6b;
        if (c8b < b) b = c8b;
        int c7b = b + r7b;
        b = cc6;
        if (cc8 < b) b = cc8;
        if (cc7 < b) b = cc7;
        if (cb6 < b) b = cb6;
        if (cb8 < b) b = cb8;
        int cb7 = b + rb7;
        b = c34;
        if (c35 < b) b = c35;
        if (c44 < b) b = c44;
        if (c36 < b) b = c36;
        int c45 = b + r45;
        b = c3a;
        if (c39 < b) b = c39;
        if (c4a < b) b = c4a;
        if (c38 < b) b = c38;
        int c49 = b + r49;
        b = c43;
        if (c53 < b) b = c53;
        if (c44 < b) b = c44;
        if (c63 < b) b = c63;
        if (c45 < b) b = c45;
        int c54 = b + r54;
        b = c4b;
        if (c5b < b) b = c5b;
        if (c4a < b) b = c4a;
        if (c6b < b) b = c6b;
        if (c49 < b) b = c49;
        int c5a = b + r5a;
        b = ca3;
        if (c93 < b) b = c93;
        if (ca4 < b) b = ca4;
        if (c83 < b) b = c83;
        int c94 = b + r94;
        b = cab;
        if (c9b < b) b = c9b;
        if (caa < b) b = caa;
        if (c8b < b) b = c8b;
        int c9a = b + r9a;
        b = cb4;
        if (cb5 < b) b = cb5;
        if (ca4 < b) b = ca4;
        if (cb6 < b) b = cb6;
        if (c94 < b) b = c94;
        int ca5 = b + ra5;
        b = cba;
        if (cb9 < b) b = cb9;
        if (caa < b) b = caa;
        if (cb8 < b) b = cb8;
        if (c9a < b) b = c9a;
        int ca9 = b + ra9;
        b = c35;
        if (c36 < b) b = c36;
        if (c37 < b) b = c37;
        if (c45 < b) b = c45;
        int c46 = b + r46;
        b = c39;
        if (c38 < b) b = c38;
        if (c37 < b) b = c37;
        if (c49 < b) b = c49;
        int c48 = b + r48;
        b = c53;
        if (c63 < b) b = c63;
        if (c73 < b) b = c73;
        if (c54 < b) b = c54;
        int c64 = b + r64;
        b = c5b;
        if (c6b < b) b = c6b;
        if (c7b < b) b = c7b;
        if (c5a < b) b = c5a;
        int c6a = b + r6a;
        b = c93;
        if (c83 < b) b = c83;
        if (c73 < b) b = c73;
        if (c94 < b) b = c94;
        int c84 = b + r84;
        b = c9b;
        if (c8b < b) b = c8b;
        if (c7b < b) b = c7b;
        if (c9a < b) b = c9a;
        int c8a = b + r8a;
        b = cb5;
        if (cb6 < b) b = cb6;
        if (cb7 < b) b = cb7;
        if (ca5 < b) b = ca5;
        int ca6 = b + ra6;
        b = cb9;
        if (cb8 < b) b = cb8;
        if (cb7 < b) b = cb7;
        if (ca9 < b) b = ca9;
        int ca8 = b + ra8;
        b = c36;
        if (c38 < b) b = c38;
        if (c37 < b) b = c37;
        if (c46 < b) b = c46;
        if (c48 < b) b = c48;
        int c47 = b + r47;
        b = c63;
        if (c83 < b) b = c83;
        if (c73 < b) b = c73;
        if (c64 < b) b = c64;
        if (c84 < b) b = c84;
        int c74 = b + r74;
        b = c6b;
        if (c8b < b) b = c8b;
        if (c7b < b) b = c7b;
        if (c6a < b) b = c6a;
        if (c8a < b) b = c8a;
        int c7a = b + r7a;
        b = cb6;
        if (cb8 < b) b = cb8;
        if (cb7 < b) b = cb7;
        if (ca6 < b) b = ca6;
        if (ca8 < b) b = ca8;
        int ca7 = b + ra7;
        b = c44;
        if (c45 < b) b = c45;
        if (c54 < b) b = c54;
        if (c46 < b) b = c46;
        if (c64 < b) b = c64;
        int c55 = b + r55;
        b = c4a;
        if (c49 < b) b = c49;
        if (c5a < b) b = c5a;
        if (c48 < b) b = c48;
        if (c6a < b) b = c6a;
        int c59 = b + r59;
        b = ca4;
        if (c94 < b) b = c94;
        if (ca5 < b) b = ca5;
        if (c84 < b) b = c84;
        if (ca6 < b) b = ca6;
        int c95 = b + r95;
        b = caa;
        if (c9a < b) b = c9a;
        if (ca9 < b) b = ca9;
        if (c8a < b) b = c8a;
        if (ca8 < b) b = ca8;
        int c99 = b + r99;
        b = c45;
        if (c46 < b) b = c46;
        if (c47 < b) b = c47;
        if (c55 < b) b = c55;
        int c56 = b + r56;
        b = c49;
        if (c48 < b) b = c48;
        if (c47 < b) b = c47;
        if (c59 < b) b = c59;
        int c58 = b + r58;
        b = c54;
        if (c64 < b) b = c64;
        if (c74 < b) b = c74;
        if (c55 < b) b = c55;
        if (c56 < b) b = c56;
        int c65 = b + r65;
        b = c5a;
        if (c6a < b) b = c6a;
        if (c7a < b) b = c7a;
        if (c59 < b) b = c59;
        if (c58 < b) b = c58;
        int c69 = b + r69;
        b = c94;
        if (c84 < b) b = c84;
        if (c74 < b) b = c74;
        if (c95 < b) b = c95;
        int c85 = b + r85;
        b = c9a;
        if (c8a < b) b = c8a;
        if (c7a < b) b = c7a;
        if (c99 < b) b = c99;
        int c89 = b + r89;
        b = ca5;
        if (ca6 < b) b = ca6;
        if (ca7 < b) b = ca7;
        if (c95 < b) b = c95;
        if (c85 < b) b = c85;
        int c96 = b + r96;
        b = ca9;
        if (ca8 < b) b = ca8;
        if (ca7 < b) b = ca7;
        if (c99 < b) b = c99;
        if (c89 < b) b = c89;
        int c98 = b + r98;
        b = c46;
        if (c48 < b) b = c48;
        if (c47 < b) b = c47;
        if (c56 < b) b = c56;
        if (c58 < b) b = c58;
        int c57 = b + r57;
        b = c64;
        if (c84 < b) b = c84;
        if (c74 < b) b = c74;
        if (c65 < b) b = c65;
        if (c85 < b) b = c85;
        int c75 = b + r75;
        b = c6a;
        if (c8a < b) b = c8a;
        if (c7a < b) b = c7a;
        if (c69 < b) b = c69;
        if (c89 < b) b = c89;
        int c79 = b + r79;
        b = ca6;
        if (ca8 < b) b = ca8;
        if (ca7 < b) b = ca7;
        if (c96 < b) b = c96;
        if (c98 < b) b = c98;
        int c97 = b + r97;
        b = c55;
        if (c56 < b) b = c56;
        if (c65 < b) b = c65;
        if (c57 < b) b = c57;
        if (c75 < b) b = c75;
        int c66 = b + r66;
        b = c59;
        if (c58 < b) b = c58;
        if (c69 < b) b = c69;
        if (c57 < b) b = c57;
        if (c79 < b) b = c79;
        int c68 = b + r68;
        b = c95;
        if (c85 < b) b = c85;
        if (c96 < b) b = c96;
        if (c75 < b) b = c75;
        if (c97 < b) b = c97;
        int c86 = b + r86;
        b = c99;
        if (c89 < b) b = c89;
        if (c98 < b) b = c98;
        if (c79 < b) b = c79;
        if (c97 < b) b = c97;
        int c88 = b + r88;
        b = c56;
        if (c58 < b) b = c58;
        if (c57 < b) b = c57;
        if (c66 < b) b = c66;
        if (c68 < b) b = c68;
        int c67 = b + r67;
        b = c65;
        if (c85 < b) b = c85;
        if (c75 < b) b = c75;
        if (c66 < b) b = c66;
        if (c86 < b) b = c86;
        if (c67 < b) b = c67;
        int c76 = b + r76;
        b = c69;
        if (c89 < b) b = c89;
        if (c79 < b) b = c79;
        if (c68 < b) b = c68;
        if (c88 < b) b = c88;
        if (c67 < b) b = c67;
        int c78 = b + r78;
        b = c96;
        if (c98 < b) b = c98;
        if (c97 < b) b = c97;
        if (c86 < b) b = c86;
        if (c88 < b) b = c88;
        if (c76 < b) b = c76;
        if (c78 < b) b = c78;
        int c87 = b + r87;


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

        b = c88;
        Direction bestD = Direction.NORTHEAST;
        if (c86 < b) {
            b = c86;
            bestD = Direction.SOUTHEAST;
        }
        if (c66 < b) {
            b = c66;
            bestD = Direction.SOUTHWEST;
        }
        if (c68 < b) {
            b = c68;
            bestD = Direction.NORTHWEST;
        }
        if (c78 < b) {
            b = c78;
            bestD = Direction.NORTH;
        }
        if (c87 < b) {
            b = c87;
            bestD = Direction.EAST;
        }
        if (c76 < b) {
            b = c76;
            bestD = Direction.SOUTH;
        }
        if (c67 < b) {
            b = c67;
            bestD = Direction.WEST;
        }
        if (rc.canMove(bestD)) {
            rc.move(bestD);
            turnsFailedToMove = 0;
        } else {
            turnsFailedToMove++;
            //frustration = 1;
        }
        if (turnsFailedToMove > 5 || (!me.equals(rc.getLocation()) && recent != null && !recent.equals(me) && rc.getLocation().isAdjacentTo(recent))) {
            frustration += 2;
            numNonFrustratedMoves = 0;
        } else {
            this.numNonFrustratedMoves++;
            if (numNonFrustratedMoves > 2)
                frustration = 1;
        }

        /*
        if (to != null)
            rc.setIndicatorLine(rc.getLocation(), to, 255, 255, 0);
        */

        int b5 = Clock.getBytecodeNum();
        //navpretty

        MapLocation current = me;
        MapLocation prev = null;
        int n = 0;
        outer:
        while (true) {
            switch (n) {
                case -54:
                    b = c25;
                    n = -67;
                    current = me.translate(-4, -2);
                    if (c26 < b) {
                        b = c26;
                        n = -66;
                    }
                    if (c34 < b) {
                        b = c34;
                        n = -55;
                    }
                    if (c35 < b) {
                        b = c35;
                        n = -54;
                    }
                    if (c44 < b) {
                        b = c44;
                        n = -42;
                    }
                    if (c36 < b) {
                        b = c36;
                        n = -53;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    break;
                case -50:
                    b = c29;
                    n = -63;
                    current = me.translate(-4, 2);
                    if (c28 < b) {
                        b = c28;
                        n = -64;
                    }
                    if (c3a < b) {
                        b = c3a;
                        n = -49;
                    }
                    if (c39 < b) {
                        b = c39;
                        n = -50;
                    }
                    if (c4a < b) {
                        b = c4a;
                        n = -36;
                    }
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    break;
                case -30:
                    b = c52;
                    n = -31;
                    current = me.translate(-2, -4);
                    if (c62 < b) {
                        b = c62;
                        n = -18;
                    }
                    if (c43 < b) {
                        b = c43;
                        n = -43;
                    }
                    if (c53 < b) {
                        b = c53;
                        n = -30;
                    }
                    if (c44 < b) {
                        b = c44;
                        n = -42;
                    }
                    if (c63 < b) {
                        b = c63;
                        n = -17;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    break;
                case -22:
                    b = c5c;
                    n = -21;
                    current = me.translate(-2, 4);
                    if (c6c < b) {
                        b = c6c;
                        n = -8;
                    }
                    if (c4b < b) {
                        b = c4b;
                        n = -35;
                    }
                    if (c5b < b) {
                        b = c5b;
                        n = -22;
                    }
                    if (c4a < b) {
                        b = c4a;
                        n = -36;
                    }
                    if (c6b < b) {
                        b = c6b;
                        n = -9;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    break;
                case 22:
                    b = c92;
                    n = 21;
                    current = me.translate(2, -4);
                    if (c82 < b) {
                        b = c82;
                        n = 8;
                    }
                    if (ca3 < b) {
                        b = ca3;
                        n = 35;
                    }
                    if (c93 < b) {
                        b = c93;
                        n = 22;
                    }
                    if (ca4 < b) {
                        b = ca4;
                        n = 36;
                    }
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    break;
                case 30:
                    b = c9c;
                    n = 31;
                    current = me.translate(2, 4);
                    if (c8c < b) {
                        b = c8c;
                        n = 18;
                    }
                    if (cab < b) {
                        b = cab;
                        n = 43;
                    }
                    if (c9b < b) {
                        b = c9b;
                        n = 30;
                    }
                    if (caa < b) {
                        b = caa;
                        n = 42;
                    }
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    break;
                case 50:
                    b = cc5;
                    n = 63;
                    current = me.translate(4, -2);
                    if (cc6 < b) {
                        b = cc6;
                        n = 64;
                    }
                    if (cb4 < b) {
                        b = cb4;
                        n = 49;
                    }
                    if (cb5 < b) {
                        b = cb5;
                        n = 50;
                    }
                    if (ca4 < b) {
                        b = ca4;
                        n = 36;
                    }
                    if (cb6 < b) {
                        b = cb6;
                        n = 51;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    break;
                case 54:
                    b = cc9;
                    n = 67;
                    current = me.translate(4, 2);
                    if (cc8 < b) {
                        b = cc8;
                        n = 66;
                    }
                    if (cba < b) {
                        b = cba;
                        n = 55;
                    }
                    if (cb9 < b) {
                        b = cb9;
                        n = 54;
                    }
                    if (caa < b) {
                        b = caa;
                        n = 42;
                    }
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    break;
                case -42:
                    b = c34;
                    n = -55;
                    current = me.translate(-3, -3);
                    if (c43 < b) {
                        b = c43;
                        n = -43;
                    }
                    if (c35 < b) {
                        b = c35;
                        n = -54;
                    }
                    if (c53 < b) {
                        b = c53;
                        n = -30;
                    }
                    if (c44 < b) {
                        b = c44;
                        n = -42;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    break;
                case -36:
                    b = c3a;
                    n = -49;
                    current = me.translate(-3, 3);
                    if (c4b < b) {
                        b = c4b;
                        n = -35;
                    }
                    if (c39 < b) {
                        b = c39;
                        n = -50;
                    }
                    if (c5b < b) {
                        b = c5b;
                        n = -22;
                    }
                    if (c4a < b) {
                        b = c4a;
                        n = -36;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    break;
                case 36:
                    b = ca3;
                    n = 35;
                    current = me.translate(3, -3);
                    if (cb4 < b) {
                        b = cb4;
                        n = 49;
                    }
                    if (c93 < b) {
                        b = c93;
                        n = 22;
                    }
                    if (cb5 < b) {
                        b = cb5;
                        n = 50;
                    }
                    if (ca4 < b) {
                        b = ca4;
                        n = 36;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    break;
                case 42:
                    b = cab;
                    n = 43;
                    current = me.translate(3, 3);
                    if (cba < b) {
                        b = cba;
                        n = 55;
                    }
                    if (c9b < b) {
                        b = c9b;
                        n = 30;
                    }
                    if (cb9 < b) {
                        b = cb9;
                        n = 54;
                    }
                    if (caa < b) {
                        b = caa;
                        n = 42;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    break;
                case -53:
                    b = c25;
                    n = -67;
                    current = me.translate(-4, -1);
                    if (c26 < b) {
                        b = c26;
                        n = -66;
                    }
                    if (c27 < b) {
                        b = c27;
                        n = -65;
                    }
                    if (c35 < b) {
                        b = c35;
                        n = -54;
                    }
                    if (c36 < b) {
                        b = c36;
                        n = -53;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    break;
                case -51:
                    b = c29;
                    n = -63;
                    current = me.translate(-4, 1);
                    if (c28 < b) {
                        b = c28;
                        n = -64;
                    }
                    if (c27 < b) {
                        b = c27;
                        n = -65;
                    }
                    if (c39 < b) {
                        b = c39;
                        n = -50;
                    }
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    break;
                case -17:
                    b = c52;
                    n = -31;
                    current = me.translate(-1, -4);
                    if (c62 < b) {
                        b = c62;
                        n = -18;
                    }
                    if (c72 < b) {
                        b = c72;
                        n = -5;
                    }
                    if (c53 < b) {
                        b = c53;
                        n = -30;
                    }
                    if (c63 < b) {
                        b = c63;
                        n = -17;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    break;
                case -9:
                    b = c5c;
                    n = -21;
                    current = me.translate(-1, 4);
                    if (c6c < b) {
                        b = c6c;
                        n = -8;
                    }
                    if (c7c < b) {
                        b = c7c;
                        n = 5;
                    }
                    if (c5b < b) {
                        b = c5b;
                        n = -22;
                    }
                    if (c6b < b) {
                        b = c6b;
                        n = -9;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    break;
                case 9:
                    b = c92;
                    n = 21;
                    current = me.translate(1, -4);
                    if (c82 < b) {
                        b = c82;
                        n = 8;
                    }
                    if (c72 < b) {
                        b = c72;
                        n = -5;
                    }
                    if (c93 < b) {
                        b = c93;
                        n = 22;
                    }
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    break;
                case 17:
                    b = c9c;
                    n = 31;
                    current = me.translate(1, 4);
                    if (c8c < b) {
                        b = c8c;
                        n = 18;
                    }
                    if (c7c < b) {
                        b = c7c;
                        n = 5;
                    }
                    if (c9b < b) {
                        b = c9b;
                        n = 30;
                    }
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    break;
                case 51:
                    b = cc5;
                    n = 63;
                    current = me.translate(4, -1);
                    if (cc6 < b) {
                        b = cc6;
                        n = 64;
                    }
                    if (cc7 < b) {
                        b = cc7;
                        n = 65;
                    }
                    if (cb5 < b) {
                        b = cb5;
                        n = 50;
                    }
                    if (cb6 < b) {
                        b = cb6;
                        n = 51;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    break;
                case 53:
                    b = cc9;
                    n = 67;
                    current = me.translate(4, 1);
                    if (cc8 < b) {
                        b = cc8;
                        n = 66;
                    }
                    if (cc7 < b) {
                        b = cc7;
                        n = 65;
                    }
                    if (cb9 < b) {
                        b = cb9;
                        n = 54;
                    }
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    break;
                case -52:
                    b = c26;
                    n = -66;
                    current = me.translate(-4, 0);
                    if (c28 < b) {
                        b = c28;
                        n = -64;
                    }
                    if (c27 < b) {
                        b = c27;
                        n = -65;
                    }
                    if (c36 < b) {
                        b = c36;
                        n = -53;
                    }
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    break;
                case -4:
                    b = c62;
                    n = -18;
                    current = me.translate(0, -4);
                    if (c82 < b) {
                        b = c82;
                        n = 8;
                    }
                    if (c72 < b) {
                        b = c72;
                        n = -5;
                    }
                    if (c63 < b) {
                        b = c63;
                        n = -17;
                    }
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    break;
                case 4:
                    b = c6c;
                    n = -8;
                    current = me.translate(0, 4);
                    if (c8c < b) {
                        b = c8c;
                        n = 18;
                    }
                    if (c7c < b) {
                        b = c7c;
                        n = 5;
                    }
                    if (c6b < b) {
                        b = c6b;
                        n = -9;
                    }
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    break;
                case 52:
                    b = cc6;
                    n = 64;
                    current = me.translate(4, 0);
                    if (cc8 < b) {
                        b = cc8;
                        n = 66;
                    }
                    if (cc7 < b) {
                        b = cc7;
                        n = 65;
                    }
                    if (cb6 < b) {
                        b = cb6;
                        n = 51;
                    }
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    break;
                case -41:
                    b = c34;
                    n = -55;
                    current = me.translate(-3, -2);
                    if (c35 < b) {
                        b = c35;
                        n = -54;
                    }
                    if (c44 < b) {
                        b = c44;
                        n = -42;
                    }
                    if (c36 < b) {
                        b = c36;
                        n = -53;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    break;
                case -37:
                    b = c3a;
                    n = -49;
                    current = me.translate(-3, 2);
                    if (c39 < b) {
                        b = c39;
                        n = -50;
                    }
                    if (c4a < b) {
                        b = c4a;
                        n = -36;
                    }
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    break;
                case -29:
                    b = c43;
                    n = -43;
                    current = me.translate(-2, -3);
                    if (c53 < b) {
                        b = c53;
                        n = -30;
                    }
                    if (c44 < b) {
                        b = c44;
                        n = -42;
                    }
                    if (c63 < b) {
                        b = c63;
                        n = -17;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    break;
                case -23:
                    b = c4b;
                    n = -35;
                    current = me.translate(-2, 3);
                    if (c5b < b) {
                        b = c5b;
                        n = -22;
                    }
                    if (c4a < b) {
                        b = c4a;
                        n = -36;
                    }
                    if (c6b < b) {
                        b = c6b;
                        n = -9;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    break;
                case 23:
                    b = ca3;
                    n = 35;
                    current = me.translate(2, -3);
                    if (c93 < b) {
                        b = c93;
                        n = 22;
                    }
                    if (ca4 < b) {
                        b = ca4;
                        n = 36;
                    }
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    break;
                case 29:
                    b = cab;
                    n = 43;
                    current = me.translate(2, 3);
                    if (c9b < b) {
                        b = c9b;
                        n = 30;
                    }
                    if (caa < b) {
                        b = caa;
                        n = 42;
                    }
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    break;
                case 37:
                    b = cb4;
                    n = 49;
                    current = me.translate(3, -2);
                    if (cb5 < b) {
                        b = cb5;
                        n = 50;
                    }
                    if (ca4 < b) {
                        b = ca4;
                        n = 36;
                    }
                    if (cb6 < b) {
                        b = cb6;
                        n = 51;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    break;
                case 41:
                    b = cba;
                    n = 55;
                    current = me.translate(3, 2);
                    if (cb9 < b) {
                        b = cb9;
                        n = 54;
                    }
                    if (caa < b) {
                        b = caa;
                        n = 42;
                    }
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    break;
                case -40:
                    b = c35;
                    n = -54;
                    current = me.translate(-3, -1);
                    if (c36 < b) {
                        b = c36;
                        n = -53;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    break;
                case -38:
                    b = c39;
                    n = -50;
                    current = me.translate(-3, 1);
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    break;
                case -16:
                    b = c53;
                    n = -30;
                    current = me.translate(-1, -3);
                    if (c63 < b) {
                        b = c63;
                        n = -17;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    break;
                case -10:
                    b = c5b;
                    n = -22;
                    current = me.translate(-1, 3);
                    if (c6b < b) {
                        b = c6b;
                        n = -9;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    break;
                case 10:
                    b = c93;
                    n = 22;
                    current = me.translate(1, -3);
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    break;
                case 16:
                    b = c9b;
                    n = 30;
                    current = me.translate(1, 3);
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    break;
                case 38:
                    b = cb5;
                    n = 50;
                    current = me.translate(3, -1);
                    if (cb6 < b) {
                        b = cb6;
                        n = 51;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    break;
                case 40:
                    b = cb9;
                    n = 54;
                    current = me.translate(3, 1);
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    break;
                case -39:
                    b = c36;
                    n = -53;
                    current = me.translate(-3, 0);
                    if (c38 < b) {
                        b = c38;
                        n = -51;
                    }
                    if (c37 < b) {
                        b = c37;
                        n = -52;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    break;
                case -3:
                    b = c63;
                    n = -17;
                    current = me.translate(0, -3);
                    if (c83 < b) {
                        b = c83;
                        n = 9;
                    }
                    if (c73 < b) {
                        b = c73;
                        n = -4;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    break;
                case 3:
                    b = c6b;
                    n = -9;
                    current = me.translate(0, 3);
                    if (c8b < b) {
                        b = c8b;
                        n = 17;
                    }
                    if (c7b < b) {
                        b = c7b;
                        n = 4;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    break;
                case 39:
                    b = cb6;
                    n = 51;
                    current = me.translate(3, 0);
                    if (cb8 < b) {
                        b = cb8;
                        n = 53;
                    }
                    if (cb7 < b) {
                        b = cb7;
                        n = 52;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    break;
                case -28:
                    b = c44;
                    n = -42;
                    current = me.translate(-2, -2);
                    if (c45 < b) {
                        b = c45;
                        n = -41;
                    }
                    if (c54 < b) {
                        b = c54;
                        n = -29;
                    }
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    break;
                case -24:
                    b = c4a;
                    n = -36;
                    current = me.translate(-2, 2);
                    if (c49 < b) {
                        b = c49;
                        n = -37;
                    }
                    if (c5a < b) {
                        b = c5a;
                        n = -23;
                    }
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    break;
                case 24:
                    b = ca4;
                    n = 36;
                    current = me.translate(2, -2);
                    if (c94 < b) {
                        b = c94;
                        n = 23;
                    }
                    if (ca5 < b) {
                        b = ca5;
                        n = 37;
                    }
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    break;
                case 28:
                    b = caa;
                    n = 42;
                    current = me.translate(2, 2);
                    if (c9a < b) {
                        b = c9a;
                        n = 29;
                    }
                    if (ca9 < b) {
                        b = ca9;
                        n = 41;
                    }
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    break;
                case -27:
                    b = c45;
                    n = -41;
                    current = me.translate(-2, -1);
                    if (c46 < b) {
                        b = c46;
                        n = -40;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    break;
                case -25:
                    b = c49;
                    n = -37;
                    current = me.translate(-2, 1);
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    break;
                case -15:
                    b = c54;
                    n = -29;
                    current = me.translate(-1, -2);
                    if (c64 < b) {
                        b = c64;
                        n = -16;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c55 < b) {
                        b = c55;
                        n = -28;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    break;
                case -11:
                    b = c5a;
                    n = -23;
                    current = me.translate(-1, 2);
                    if (c6a < b) {
                        b = c6a;
                        n = -10;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c59 < b) {
                        b = c59;
                        n = -24;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    break;
                case 11:
                    b = c94;
                    n = 23;
                    current = me.translate(1, -2);
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    break;
                case 15:
                    b = c9a;
                    n = 29;
                    current = me.translate(1, 2);
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    break;
                case 25:
                    b = ca5;
                    n = 37;
                    current = me.translate(2, -1);
                    if (ca6 < b) {
                        b = ca6;
                        n = 38;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c95 < b) {
                        b = c95;
                        n = 24;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case 27:
                    b = ca9;
                    n = 41;
                    current = me.translate(2, 1);
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c99 < b) {
                        b = c99;
                        n = 28;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case -26:
                    b = c46;
                    n = -40;
                    current = me.translate(-2, 0);
                    if (c48 < b) {
                        b = c48;
                        n = -38;
                    }
                    if (c47 < b) {
                        b = c47;
                        n = -39;
                    }
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    break;
                case -2:
                    b = c64;
                    n = -16;
                    current = me.translate(0, -2);
                    if (c84 < b) {
                        b = c84;
                        n = 10;
                    }
                    if (c74 < b) {
                        b = c74;
                        n = -3;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    break;
                case 2:
                    b = c6a;
                    n = -10;
                    current = me.translate(0, 2);
                    if (c8a < b) {
                        b = c8a;
                        n = 16;
                    }
                    if (c7a < b) {
                        b = c7a;
                        n = 3;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    break;
                case 26:
                    b = ca6;
                    n = 38;
                    current = me.translate(2, 0);
                    if (ca8 < b) {
                        b = ca8;
                        n = 40;
                    }
                    if (ca7 < b) {
                        b = ca7;
                        n = 39;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case -14:
                    b = c55;
                    n = -28;
                    current = me.translate(-1, -1);
                    if (c56 < b) {
                        b = c56;
                        n = -27;
                    }
                    if (c65 < b) {
                        b = c65;
                        n = -15;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    break;
                case -12:
                    b = c59;
                    n = -24;
                    current = me.translate(-1, 1);
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c69 < b) {
                        b = c69;
                        n = -11;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    break;
                case 12:
                    b = c95;
                    n = 24;
                    current = me.translate(1, -1);
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c96 < b) {
                        b = c96;
                        n = 25;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case 14:
                    b = c99;
                    n = 28;
                    current = me.translate(1, 1);
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case -13:
                    b = c56;
                    n = -27;
                    current = me.translate(-1, 0);
                    if (c58 < b) {
                        b = c58;
                        n = -25;
                    }
                    if (c57 < b) {
                        b = c57;
                        n = -26;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    break;
                case -1:
                    b = c65;
                    n = -15;
                    current = me.translate(0, -1);
                    if (c85 < b) {
                        b = c85;
                        n = 11;
                    }
                    if (c75 < b) {
                        b = c75;
                        n = -2;
                    }
                    if (c66 < b) {
                        b = c66;
                        n = -14;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case 1:
                    b = c69;
                    n = -11;
                    current = me.translate(0, 1);
                    if (c89 < b) {
                        b = c89;
                        n = 15;
                    }
                    if (c79 < b) {
                        b = c79;
                        n = 2;
                    }
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case 13:
                    b = c96;
                    n = 25;
                    current = me.translate(1, 0);
                    if (c98 < b) {
                        b = c98;
                        n = 27;
                    }
                    if (c97 < b) {
                        b = c97;
                        n = 26;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                case 0:
                    b = c66;
                    n = -14;
                    current = me.translate(0, 0);
                    if (c68 < b) {
                        b = c68;
                        n = -12;
                    }
                    if (c86 < b) {
                        b = c86;
                        n = 12;
                    }
                    if (c88 < b) {
                        b = c88;
                        n = 14;
                    }
                    if (c67 < b) {
                        b = c67;
                        n = -13;
                    }
                    if (c76 < b) {
                        b = c76;
                        n = -1;
                    }
                    if (c78 < b) {
                        b = c78;
                        n = 1;
                    }
                    if (c87 < b) {
                        b = c87;
                        n = 13;
                    }
                    break;
                default:
                    break outer;
            }
            // if (prev != null) rc.setIndicatorLine(prev, current, 0, 255, 255);
            prev = current;
        }
        lastNavEndpoint = prev;

        //end navpretty
        lastMoveTowardTarget = to;
        lastNavUse = rc.getLocation();

        int b6 = Clock.getBytecodeNum();
        //rc.setIndicatorString("init "+(b1-b0)+" r "+(b2-b1)+" i "+(b3-b2) + " i2 "+(b4-b3)+" m "+(b5-b4)+" f "+(b6-b5));
        rc.setIndicatorString("fr " + frustration + " t " + this.numNonFrustratedMoves + " " + c88 + " " + c87 + " " + c86 + " " + c76 + " " + c66 + " " + c67 + " " + c68 + " " + c78);

    }

    public boolean isAttacker(RobotType rt) {
        return rt != LABORATORY && rt != MINER;
    }
    public boolean isAttacker(RobotInfo ri) {
        return isAttacker(ri.type);
    }
}
