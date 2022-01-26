package matir;

import battlecode.common.*;

import java.util.ArrayList;

import static battlecode.common.GameConstants.GAME_MAX_NUMBER_OF_ROUNDS;
import static battlecode.common.RobotType.*;
import static java.lang.Math.*;

public class Archon extends Robot {
    private int myHQIndex;
    private int initialArchonCount = rc.getArchonCount();
    private static final int EXPECTED_MOVE_COST = 5;
    private int lastTurnMoney = 0;
    private int miners = 0;
    private int soldiers = 0;
    private int totalSpent = 0;
    private boolean relocating = false;
    private MapLocation relocTarget;
    private AnomalyScheduleEntry[] anomalies;
    private int anomalyIndex = 0;
    private int vortexIndex = 0;
    private int income = 0;
    private int prevIncome = 0;
    int totalHQ;

    Archon(RobotController r) throws GameActionException {
        super(r);
        int i;
        double badness = (max(rc.getMapWidth() - myLoc.x - 1, myLoc.x) +
                max(rc.getMapHeight() - myLoc.y - 1, myLoc.y));

        for (i = 0; rc.readSharedArray(i + INDEX_MY_HQ) > 0 && i < 4; i++) ;
        if (i < 4) {
            myHQIndex = i;
            rc.setIndicatorString(String.valueOf(i));
            rc.writeSharedArray(INDEX_HQBAD + i, (int) badness);
            rc.writeSharedArray(INDEX_MY_HQ + i,
                    locToInt(rc.getLocation()));
        } else {
            rc.disintegrate(); //uh oh something went very wrong
        }

        for (i = 0; i < NUM_ENEMY_UNIT_CHUNKS; i++) {
            rc.writeSharedArray(i + INDEX_ENEMY_UNIT_LOCATION, 0xFFFF);
        }

        for (i = 0; i < NUM_ENEMY_ETC_CHUNKS; i++) {
            rc.writeSharedArray(i + INDEX_ENEMY_ETC_LOCATION, 0xFFFF);
        }

        for (i = 0; i < NUM_MY_UNIT_CHUNKS; i++) {
            rc.writeSharedArray(i + INDEX_MY_UNIT_LOCATIONS, 0xFFFF);
        }

        for (i = 0; i < NUM_LEAD_DEPOSIT_INTS; i++) {
            rc.writeSharedArray(i + INDEX_LEAD_DEPOSITS, 0xFFFF);
        }

        totalHQ = rc.getArchonCount();

        anomalies = rc.getAnomalySchedule();
        getNextVortex();
    }

    void getNextVortex() {
        while (vortexIndex < anomalies.length) {
            if (anomalies[vortexIndex].anomalyType == AnomalyType.VORTEX &&
                    anomalies[vortexIndex].roundNumber > rc.getRoundNum()) break;
            vortexIndex++;
        }
    }

    boolean die = false;

    public void turn() throws GameActionException {
        int income = rc.readSharedArray(INDEX_INCOME) / 2;
        int liveMiners = rc.readSharedArray(INDEX_LIVE_MINERS) / 2;
        //if(rc.getRoundNum() > 500 && rc.getTeamLeadAmount(rc.getTeam()) < rc.getTeamLeadAmount(rc.getTeam().opponent())) rc.resign();
        //if(liveMiners < 18 && rc.getTeamLeadAmount(rc.getTeam())>RobotType.MINER.buildCostLead) buildMiner();
        //if(DEBUG) return;
        int money = rc.getTeamLeadAmount(rc.getTeam());
        int rawIncome = money - lastTurnMoney;
        lastTurnMoney = money;

        if (rc.getRoundNum() == 1) {
            buildMiner();
            updateEnemyHQs();
            return;
        }

        if (rc.getRoundNum() == 2) {
            int best = 1000000, bi = 0;
            for (int i = 0; i < totalHQ; i++) {
                int badi = rc.readSharedArray(INDEX_HQBAD + i);
                if (badi < best) {
                    best = badi;
                    bi = i;
                }
            }
            if (bi != myHQIndex) {
                die = true;
            } else {
                rc.writeSharedArray(INDEX_ARCHON_LOC, locToInt(myLoc));
                considerRelocate();
            }
        }

        if (die && readMisc(BIT_RELOCATE) == 0) {
            buildMiner();
            RobotInfo[] nearby = rc.senseNearbyRobots(MINER.visionRadiusSquared,
                    rc.getTeam());
            boolean nearbyminer = false;
            for (RobotInfo rb : nearby) {
                if (rb.type == MINER) {
                    nearbyminer = true;
                    break;
                }
            }
            if (nearbyminer && nearby.length >= 2) rc.disintegrate();
            return;
        }

        //if(die && rng.nextDouble() * (rc.getArchonCount() - 1) < 1) return;

        if (!die) rc.writeSharedArray(INDEX_ARCHON_LOC, locToInt(myLoc));

        //int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;

        if (anomalyIndex < anomalies.length) {
            if (anomalies[anomalyIndex].roundNumber <= rc.getRoundNum()) {
                getNextVortex();
                if (anomalies[anomalyIndex].anomalyType == AnomalyType.VORTEX) {
                    considerRelocate();
                }

                anomalyIndex++;
            }
        }

        if (!die) {
            continueRelocate();
        }

        if (DEBUG) {
            MapLocation enemyLoc = intToChunk(rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION + rc.getRoundNum() % Robot.NUM_ENEMY_UNIT_CHUNKS));
            rc.setIndicatorString(myHQIndex + " income=" + income + " miners=" + liveMiners + " enemy=" + enemyLoc);
        }
        boolean myTurn = true;
        // if we're under attack, override this and always build
        boolean underAttack = rc.senseNearbyRobots(ARCHON.visionRadiusSquared,
                rc.getTeam().opponent()).length > 0;

        int builders = readMisc(BIT_BUILDER, NUM_BUILDER);
        if (!underAttack && rc.getRoundNum() >
                (70 - rc.getMapWidth()) + (70 - rc.getMapHeight()) && builders == 0) {
            if (buildInDirection(RobotType.BUILDER, rc.getLocation()
                    .directionTo(new MapLocation(rc.getMapWidth() / 2,
                            rc.getMapHeight() / 2)).opposite())) {
            }
        }

        int mapLead = countLead();
        int max_miners = (int) ((pow(rc.getMapHeight() * rc.getMapHeight(), 0.8)
                / 200.0) * pow(rc.getRoundNum(), 0.3));
        int initTurns = 10 + (max(rc.getMapWidth() - myLoc.x - 1, myLoc.x) +
                max(rc.getMapHeight() - myLoc.y - 1, myLoc.y)) / 20;
        double minerToSoldier = max(0.7 - (rc.getRoundNum() / 5000.0) -
                (800.0 / (rc.getMapWidth() * rc.getMapHeight())), 0.01);

        int numLabs = readMisc(BIT_LAB, NUM_LAB);

        if (buildInDirection(RobotType.SAGE, rc.getLocation().directionTo(
                new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2)))) {
            soldiers++;
        } else {
            if (!underAttack && rc.getTeamLeadAmount(rc.getTeam()) < 150 &&
                    (max_miners / 1.5 > liveMiners ||
                            (income > liveMiners * 50 && liveMiners < max_miners) ||
                            //              (income > liveMiners * 80) ||
                            rc.getRoundNum() < initTurns ||
                            soldiers * minerToSoldier > miners)) {
                if (buildMiner())
                    miners++;
            } else {
                if (underAttack || rc.getRoundNum() <
                        (140 -rc.getMapWidth()-rc.getMapHeight()) ||
                        rc.getTeamLeadAmount(rc.getTeam()) > 255 ||
                builders == 0 || rc.getRoundNum() < numLabs * 50) {
                    if (buildInDirection(RobotType.SOLDIER,
                            rc.getLocation().directionTo(new MapLocation(
                                    rc.getMapWidth() / 2, rc.getMapHeight() / 2))))
                        soldiers++;
                }
            }
        }

        repair();

        writeMisc(BIT_LAB, 0, NUM_LAB);
        writeMisc(BIT_BUILDER, 0, NUM_BUILDER);
        if(rc.getRoundNum() % 7 == 0) saveInfo();

        if (rc.getRoundNum() % 16 == 0) {
            removeFriendlyUnitLocations();
            removeOldEnemyUnitLocations();
        }
        if (rc.getRoundNum() % 100 == 0) {
            writeMisc(BIT_HEALING, 0);
        }

        rc.writeSharedArray(myHQIndex + Robot.INDEX_HQ_SPENDING, 0x4000 | ((rc.getRoundNum() % 4) << 12) | (totalSpent >> 4));
        lastTurnMoney = rc.getTeamLeadAmount(rc.getTeam());
        if (rc.getRoundNum() % 160 == 0) {
            super.clearUnexploredChunks();
        }
    }

    void continueRelocate() throws GameActionException {
        if (!relocating) return;

        considerRelocate(); // for possible better relocation spot

        if (rc.getMode() == RobotMode.TURRET) {
            if (rc.canTransform()) rc.transform();
        } else {
            if (rc.getLocation() == relocTarget) {
                if (rc.canTransform()) {
                    rc.transform();
                    relocating = false;
                    writeMisc(BIT_RELOCATE, 0);
                }
            } else {
                moveToward(relocTarget);
                myLoc = rc.getLocation();
                rc.writeSharedArray(INDEX_ARCHON_LOC, locToInt(myLoc));
            }
        }
    }

    int countLead() throws GameActionException {
        if (rc.getRoundNum() < 10) return 0;

        int leadOnMap = 0;
        for (int i = 0; i < NUM_LEAD_DEPOSITS; i++) {
            leadOnMap += chunkToLead(savedLead[i]);
        }

        return leadOnMap;
    }

    int healTarget;

    private void repair() throws GameActionException {
        if (!rc.isActionReady()) return;

        if (rc.canSenseRobot(healTarget)) {
            RobotInfo rb = rc.senseRobot(healTarget);
            if (rb.health < rb.type.getMaxHealth(1)) {
                if (rc.canRepair(rb.location)) {
                    rc.repair(rb.location);
                    return;
                }
            }
        }

        RobotInfo best = rc.senseRobot(rc.getID());

        for (RobotInfo r : rc.senseNearbyRobots(ARCHON.visionRadiusSquared,
                rc.getTeam())) {
            if (r.health == r.type.getMaxHealth(r.level))
                continue;

            if (r.mode != RobotMode.DROID) continue;

            if (best.mode != RobotMode.DROID) {
                best = r;
            } else {
                if (r.type == SAGE && best.type != SAGE) {
                    best = r;
                } else if (r.type == SOLDIER && best.type != SOLDIER) {
                    best = r;
                } else if (r.health > best.health) {
                    best = r;
                }
            }
        }

        if (rc.canRepair(best.location)) {
            rc.repair(best.location);
            healTarget = best.ID;
        }
    }

    //build a miner toward the nearest deposit
    private boolean buildMiner() throws GameActionException {
        MapLocation[] locs = rc.senseNearbyLocationsWithLead(ARCHON.visionRadiusSquared, 5);
        if (locs.length == 0 || rc.senseLead(myLoc) >= 5)
            return build(MINER);
        MapLocation closest = locs[0];
        for (MapLocation l : locs) {
            if (l.distanceSquaredTo(rc.getLocation()) < closest.distanceSquaredTo(rc.getLocation()))
                closest = l;
        }

        return buildInDirection(MINER, rc.getLocation().directionTo(closest));
    }

    private boolean buildInDirection(RobotType t, Direction d) throws GameActionException {
        if (rc.getTeamLeadAmount(rc.getTeam()) < t.buildCostLead)
            return false;
        Direction[] dirs = {d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight(),
                d.rotateLeft().rotateLeft().rotateLeft(), d.rotateRight().rotateRight().rotateRight(), d.opposite()};
        double[] suitability = {1, .5, .5, .3, .3, .2, .2, .1};
        for (int i = 0; i < 8; i++) {
            MapLocation l = rc.getLocation().add(dirs[i]);
            if (rc.onTheMap(l))
                suitability[i] /= 10 + rc.senseRubble(l);
        }
        double best = 0;
        Direction bestD = null;
        for (int i = 0; i < 8; i++) {
            if (suitability[i] > best && rc.canBuildRobot(t, dirs[i])) {
                best = suitability[i];
                bestD = dirs[i];
            }
        }
        if (bestD == null)
            return false;
        rc.buildRobot(t, bestD);
        totalSpent += t.buildCostLead;
        return true;
    }

    //builds in a random direction if legal
    private boolean build(RobotType t) throws GameActionException {
        if (rc.getTeamLeadAmount(rc.getTeam()) < t.getLeadWorth(1))
            return false;

        ArrayList<Direction> buildables = new ArrayList<>();
        for (Direction d : directions) {
            if (rc.canBuildRobot(t, d))
                buildables.add(d);
        }
        if (buildables.size() == 0) return false;
        Direction[] dirs = new Direction[buildables.size()];
        for (int i = 0; i < buildables.size(); i++)
            dirs[i] = buildables.get(i);

        Direction dir = randDirByWeight(dirs, rubbleWeight);

        if (rc.canBuildRobot(t, dir)) {
            rc.buildRobot(t, dir);
            totalSpent += t.buildCostLead;
            return true;
        }

        return false;
    }

    void considerRelocate() throws GameActionException {
        if (die) return;
        int nextEvent = vortexIndex < anomalies.length ?
                anomalies[vortexIndex].roundNumber : GAME_MAX_NUMBER_OF_ROUNDS;
        int round = rc.getRoundNum();
        int roundsLeft = nextEvent - round;
        MapLocation here = rc.getLocation();
        int curRubble = rc.senseRubble(here);
        int bestRubble = curRubble;
        relocTarget = here;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -(int) sqrt(34 - dx ^ 2); dy <= sqrt(34 - dx ^ 2); ++dy) {
                MapLocation newLoc = here.translate(dx, dy);
                int newRubble = (int) sqrt(here.distanceSquaredTo(newLoc)) * EXPECTED_MOVE_COST +
                        (rc.canSenseLocation(newLoc) ? rc.senseRubble(newLoc) : 100);
                if (newRubble < bestRubble) {
                    bestRubble = newRubble;
                    relocTarget = newLoc;
                }
            }
        }

        double curMul = 1 + curRubble / 10, bestMul = 1 + bestRubble / 10;
        int curTurnsLeft = (int) (roundsLeft / curMul);
        if (relocating) curTurnsLeft -= curMul * rc.getTransformCooldownTurns();
        int bestTurnsLeft = (int) ((roundsLeft - (curMul + bestMul) * rc.getTransformCooldownTurns()
                - sqrt(here.distanceSquaredTo(relocTarget)) * EXPECTED_MOVE_COST) / bestMul);

        double interestFactor = rc.getArchonCount() > 1 ? 1 : 2;

        if (bestTurnsLeft > interestFactor * curTurnsLeft) {
            relocating = true;
            writeMisc(BIT_RELOCATE, 1);
            rc.setIndicatorString("Relocating");
        }
    }
}
