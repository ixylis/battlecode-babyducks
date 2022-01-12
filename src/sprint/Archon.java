package sprint;

import battlecode.common.*;

import java.util.ArrayList;

import static battlecode.common.GameConstants.GAME_MAX_NUMBER_OF_ROUNDS;
import static battlecode.common.RobotType.*;
import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class Archon extends Robot {
    private int myHQIndex;
    private int initialArchonCount = rc.getArchonCount();
    private static final int EXPECTED_MOVE_COST = 5;
    private int lastTurnMoney = 0;
    private int miners = 0;
    private int totalSpent = 0;
    private boolean relocating = false;
    private MapLocation relocTarget;
    private AnomalyScheduleEntry[] anomalies;
    private int anomalyIndex = 0;
    private int vortexIndex = 0;
    private int income = 0;
    private int prevIncome = 0;


    Archon(RobotController r) throws GameActionException {
        super(r);
        int i;
        for(i=INDEX_MY_HQ;rc.readSharedArray(i)>0 && i<INDEX_MY_HQ+4; i++);
        if(i<INDEX_MY_HQ+4) {
            myHQIndex = i;
            rc.writeSharedArray(i, Robot.locToInt(rc.getLocation()));
        } else {
            rc.disintegrate(); //uh oh something went very wrong
        }

        anomalies = rc.getAnomalySchedule();
        getNextVortex();
        considerRelocate();
    }

    private void getNextVortex() {
        while(vortexIndex < anomalies.length) {
            if(anomalies[vortexIndex].anomalyType == AnomalyType.VORTEX &&
                    anomalies[vortexIndex].roundNumber > rc.getRoundNum()) break;
            vortexIndex++;
        }
    }

    public void turn() throws GameActionException {

        if(anomalyIndex < anomalies.length) {
            if(anomalies[anomalyIndex].roundNumber <= rc.getRoundNum()) {
                getNextVortex();
                if(anomalies[anomalyIndex].anomalyType == AnomalyType.VORTEX) {
                    considerRelocate();
                }

                anomalyIndex++;
            }
        }

        if(prevIncome / rc.getArchonCount() < 200) considerRelocate();

        if(relocating) {
            considerRelocate(); // for possible better relocation spot

            if(rc.getMode() == RobotMode.TURRET) {
                if(rc.canTransform()) rc.transform();
            } else {
                if(rc.getLocation() == relocTarget) {
                    if(rc.canTransform()) {
                        rc.transform();
                        relocating = false;
                    }
                } else {
                    moveToward(relocTarget);
                }
            }

            return;
        }

        //int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;
        int income = rc.readSharedArray(INDEX_INCOME)/2;
        prevIncome = income;
        int liveMiners = rc.readSharedArray(INDEX_LIVE_MINERS)/2;

        if(DEBUG) {
            MapLocation enemyLoc = Robot.intToChunk(rc.readSharedArray(INDEX_ENEMY_LOCATION+rc.getRoundNum()% Robot.NUM_ENEMY_SOLDIER_CHUNKS));
            rc.setIndicatorString(myHQIndex+" income="+income+" miners="+liveMiners+" enemy="+enemyLoc);
        }
        //determine if it's my turn to build
        //it's my turn if every other archon has spent at least my spending minus 100
        boolean myTurn = true;
        for(int i=0;i<4;i++) {
            if(i == myHQIndex)
                continue;
            int x = rc.readSharedArray(INDEX_HQ_SPENDING + i);
            if((x&0x4000)==0) //this archon is dead
                continue;
            if(((x&0x3000)>>12) == (rc.getRoundNum()+1)%4) { //this archon didn't update for three rounds, so it's dead
                rc.writeSharedArray(INDEX_HQ_SPENDING + i, 0);
                continue;
            }
            if(((x&0xfff)<<4) < totalSpent - 100)
                myTurn = false;
        }
        if(myTurn) {
            if(rc.getTeamLeadAmount(rc.getTeam()) < 1000 && (income>(liveMiners-5)*25 || rc.getRoundNum()<20)) {
                if(build(MINER))
                    miners++;
            } else {
//                RobotInfo [] robots = rc.senseNearbyRobots(ARCHON.visionRadiusSquared, rc.getTeam());
//                int numBuilders = 0;
//                for (RobotInfo robot : robots) {
//                    if (robot.type == RobotType.BUILDER) numBuilders ++;
//                }
//                Direction preferredBuilder = rc.getLocation().directionTo(new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2));
//                if (rc.canBuildRobot(RobotType.BUILDER, preferredBuilder) && rc.getTeamLeadAmount(rc.getTeam()) > MAX_LEAD * (numBuilders + 1))
//                    rc.buildRobot(RobotType.BUILDER, preferredBuilder);
                build(RobotType.SOLDIER);
            }
        }
        super.removeOldEnemySoldierLocations();
        super.updateEnemySoliderLocations();
        rc.writeSharedArray(myHQIndex + Robot.INDEX_HQ_SPENDING, 0x4000 | ((rc.getRoundNum()%4)<<12) | (totalSpent>>4));
        lastTurnMoney = rc.getTeamLeadAmount(rc.getTeam());

        if(rc.isActionReady()) {
            RobotInfo best = rc.senseRobot(rc.getID());
            for(RobotInfo r : rc.senseNearbyRobots(ARCHON.visionRadiusSquared, rc.getTeam())) {
                if(r.mode == RobotMode.DROID) {
                    if(best.mode != RobotMode.DROID) {
                        best = r;
                    } else {
                        if(r.type == SOLDIER && best.type != SOLDIER) {
                            best = r;
                        } else if(r.health < best.health) {
                            best = r;
                        }
                    }
                }
            }

            if(rc.canRepair(best.location)) {
                rc.repair(best.location);
            }
        }
    }

    //builds in a random direction if legal
    private boolean build(RobotType t) throws GameActionException {
        if(rc.getTeamLeadAmount(rc.getTeam()) < t.getLeadWorth(1))
            return false;

        ArrayList<Direction> buildables = new ArrayList<>();
        for(Direction d : directions) {
            if(rc.canBuildRobot(t, d))
                buildables.add(d);
        }
        if(buildables.size() == 0) return false;
        Direction[] dirs = new Direction[buildables.size()];
        for(int i = 0; i < buildables.size(); i++)
            dirs[i] = buildables.get(i);

        Direction dir = randDirByWeight(dirs, rubbleWeight);

        if(rc.canBuildRobot(t, dir)) {
            rc.buildRobot(t, dir);
            totalSpent += t.buildCostLead;
            return true;
        }

        return false;
    }

    private void considerRelocate() throws GameActionException {
        int nextEvent = vortexIndex < anomalies.length ?
                anomalies[vortexIndex].roundNumber : GAME_MAX_NUMBER_OF_ROUNDS;
        int round = rc.getRoundNum();
        int roundsLeft = nextEvent - round;
        MapLocation here = rc.getLocation();
        int curRubble = rc.senseRubble(here);
        int bestRubble = curRubble;
        relocTarget = here;
        for(int dx = -5; dx <= 5; dx++) {
            for(int dy = -(int)sqrt(34-dx^2); dy <= sqrt(34-dx^2); ++dy) {
                MapLocation newLoc = here.translate(dx, dy);
                int newRubble = (int) sqrt(here.distanceSquaredTo(newLoc)) * EXPECTED_MOVE_COST +
                        (rc.canSenseLocation(newLoc) ? rc.senseRubble(newLoc) : 100);
                if(newRubble < bestRubble) {
                    bestRubble = newRubble;
                    relocTarget = newLoc;
                }
            }
        }

        double curMul = 1 + curRubble / 10, bestMul = 1 + bestRubble / 10;
        int curTurnsLeft = (int) (roundsLeft / curMul);
        if(relocating) curTurnsLeft -= curMul * rc.getTransformCooldownTurns();
        int bestTurnsLeft = (int) ((roundsLeft - (curMul + bestMul) * rc.getTransformCooldownTurns()
                - sqrt(here.distanceSquaredTo(relocTarget)) * EXPECTED_MOVE_COST) / bestMul);

        double interestFactor = 2.0 - rc.getArchonCount() * 0.2 +
                max(200 - income / rc.getArchonCount(), 0) * 0.05;

        if(bestTurnsLeft > interestFactor * curTurnsLeft) {
            relocating = true;
            rc.setIndicatorString("Relocating");
        }
    }
}
