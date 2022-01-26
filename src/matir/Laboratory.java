package matir;

import battlecode.common.*;
import static battlecode.common.GameConstants.*;
import static battlecode.common.RobotType.*;
import static java.lang.Math.*;

public class Laboratory extends Robot {
    int lastActive;
    private boolean relocating = false;
    private MapLocation relocTarget;
    private int anomalyIndex = 0;
    private int vortexIndex = 0;
    private static final int EXPECTED_MOVE_COST = 5;
    AnomalyScheduleEntry[] anomalies;

    Laboratory(RobotController r) throws GameActionException {
        super(r);
        lastActive = rc.getRoundNum();

        anomalies = rc.getAnomalySchedule();
        getNextVortex();
    }

    public void turn() throws GameActionException {
        int income = rc.readSharedArray(INDEX_INCOME) / 80;
        int numLabs = readMisc(BIT_LAB, NUM_LAB);
        writeMisc(BIT_LAB, NUM_LAB, numLabs + 1);
        if (rc.getMode() == RobotMode.PROTOTYPE) return;

        // convert lead to gold if the conversion rate is better than 6:1
        if (rc.getTransmutationRate() <= 6) {
            if (rc.canTransmute())
                rc.transmute();
        }

        considerRelocate();
        continueRelocate();
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
                }
            } else {
                moveToward(relocTarget);
                myLoc = rc.getLocation();
            }
        }

    }

    void getNextVortex() {
        while (vortexIndex < anomalies.length) {
            if (anomalies[vortexIndex].anomalyType == AnomalyType.VORTEX &&
                    anomalies[vortexIndex].roundNumber > rc.getRoundNum()) break;
            vortexIndex++;
        }
    }

    private void considerRelocate() throws GameActionException {
        int nextEvent = vortexIndex < anomalies.length ?
                anomalies[vortexIndex].roundNumber : GAME_MAX_NUMBER_OF_ROUNDS;
        int round = rc.getRoundNum();
        int roundsLeft = nextEvent - round;
        MapLocation here = rc.getLocation();
        int curRubble = rc.senseRubble(here);
        int bestRubble = curRubble;
        double cmex = myLoc.x, cmey = myLoc.y, me = 0, mme = 0;
        double cmfx = myLoc.x, cmfy = myLoc.y, mf = 0, mmf = 0;

        RobotInfo[] enemies = rc.senseNearbyRobots(
                LABORATORY.visionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] friends = rc.senseNearbyRobots(
                LABORATORY.visionRadiusSquared, rc.getTeam());

        for(RobotInfo enemy : enemies) {
            mme = power(enemy) / myLoc.distanceSquaredTo(enemy.location);
            if(mme == 0) continue;

            cmex = (cmex * me + enemy.location.x * mme) / (me + mme);
            cmey = (cmey * me + enemy.location.y * mme) / (me + mme);

            me += mme;
        }

        for(RobotInfo friend : friends) {
            mmf = 1;

            cmfx = (cmfx * me + friend.location.x * mmf) / (mf + mmf);
            cmfy = (cmfy * mf + friend.location.y * mmf) / (mf + mmf);

            mf += mmf;
        }

        if(relocTarget == null) relocTarget = here;
        Direction badDir = relocTarget.directionTo(
                        new MapLocation((int)cmex, (int)cmey));

        if(me > 0) relocTarget = relocTarget.subtract(badDir)
                .subtract(badDir).subtract(badDir).subtract(badDir);

        badDir = relocTarget.directionTo(
                new MapLocation((int)cmfx, (int)cmfy));

        if(mf > 0) relocTarget = relocTarget.subtract(badDir).subtract(badDir);

        for (MapLocation newLoc : rc.getAllLocationsWithinRadiusSquared(
                relocTarget, 13)) {
            int newRubble = (int) sqrt(here.distanceSquaredTo(newLoc)) * EXPECTED_MOVE_COST +
                    (rc.canSenseLocation(newLoc) ? rc.senseRubble(newLoc) : 100);

            if (newRubble < bestRubble) {
                bestRubble = newRubble;
                relocTarget = newLoc;
            }
        }

        double curMul = 1 + curRubble / 10.0, bestMul = 1 + bestRubble / 10.0;
        int curTurnsLeft = (int) (roundsLeft / curMul);
        if (relocating) curTurnsLeft -= curMul * rc.getTransformCooldownTurns();
        double moveCost = 1 + rc.senseRubble(myLoc) / 10.0;
        int bestTurnsLeft = (int) ((roundsLeft - (curMul + bestMul) * rc.getTransformCooldownTurns()
                - sqrt(here.distanceSquaredTo(relocTarget)) * moveCost) / bestMul);

        double interestFactor = 1.2;

        if (bestTurnsLeft > interestFactor * curTurnsLeft) {
            relocating = true;
        }
    }

}
