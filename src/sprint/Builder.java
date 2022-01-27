package sprint;

import battlecode.common.*;
import static battlecode.common.RobotType.*;
import java.util.ArrayList;

public class Builder extends Robot {
    Builder(RobotController r) throws GameActionException {
        super(r);
    }
    private MapLocation hqLoc = null;
    private ArrayList<MapLocation> labLocs = new ArrayList<MapLocation>(); // store all labs we've built
    private boolean builtLab = false;
    public void turn() throws GameActionException {
        writeMisc(BIT_BUILDER, readMisc(BIT_BUILDER, NUM_BUILDER) + 1, NUM_BUILDER);
        rc.setIndicatorString("Cooldown: " + rc.getActionCooldownTurns());
        MapLocation me = rc.getLocation();
        if (hqLoc == null) {
            RobotInfo [] robots = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    hqLoc = robot.location;
                }
            }
        }
        // if we can repair a building, do that
        boolean nearbyBuilding = false;
        MapLocation buildingToRepair = null;
        RobotInfo [] friends = rc.senseNearbyRobots(5, rc.getTeam());
        for (RobotInfo robot : friends) {
            if (robot.health < robot.type.health) {
                if (rc.canRepair(robot.location)) {
                    rc.setIndicatorString("Repairing building");
                    nearbyBuilding = true;
                    if (rc.canRepair(robot.location)) rc.repair(robot.location);
                    return;
                } else if (robot.type == RobotType.WATCHTOWER || robot.type == RobotType.ARCHON || robot.type == RobotType.LABORATORY) {
                    buildingToRepair = robot.location;
                    nearbyBuilding = true;
                }
            }
        }
        if (nearbyBuilding) {
            // try to find a low-rubble square within range of building
            int rubble = rc.senseRubble(me);
            for (Direction dir : directions) {
                MapLocation newLoc = me.add(dir);
                if (rc.canMove(dir) && rc.senseRubble(newLoc) < rubble && newLoc.distanceSquaredTo(buildingToRepair) < 5)
                    rc.move(dir);
            }
            return;
        }
        // update labLocs with labs seen on board (from other builders/labs moving around)
        friends = rc.senseNearbyRobots(RobotType.BUILDER.visionRadiusSquared, rc.getTeam());
        for (RobotInfo robot : friends) {
            if (robot.type == RobotType.LABORATORY && !labLocs.contains(robot.location))
                labLocs.add(robot.location);
        }
        // go to corner of board and build a lab
        MapLocation nearestCorner = null;
        int distanceToNearestCorner = Integer.MAX_VALUE;
        for (MapLocation corner : corners) {
            boolean replaceCorner = true;
            replaceCorner &= (me.distanceSquaredTo(corner) < distanceToNearestCorner);
            replaceCorner &= (hqLoc == null || corner.distanceSquaredTo(hqLoc) > RobotType.LABORATORY.visionRadiusSquared);
            for (MapLocation labLoc : labLocs)
                replaceCorner &= (corner.distanceSquaredTo(labLoc) > RobotType.LABORATORY.visionRadiusSquared);
            if (replaceCorner) {
                nearestCorner = corner;
                distanceToNearestCorner = me.distanceSquaredTo(corner);
            }
        }
        // if no corner works, default to the first one
        // this means we have already built in all four corners, so we're probably winning
        if (nearestCorner == null) nearestCorner = corners[0];
        // build on zero-rubble squares far from other labs/HQ or, failing that, squares in the corner
        Direction mediocreDir = null;
        for (Direction dir : Direction.allDirections()) {
            MapLocation newLoc = me.add(dir);
            boolean goodSquare = true;
            goodSquare &= (hqLoc == null || newLoc.distanceSquaredTo(hqLoc) > RobotType.LABORATORY.visionRadiusSquared);
            for (MapLocation labLoc : labLocs)
                goodSquare &= (newLoc.distanceSquaredTo(labLoc) > RobotType.LABORATORY.visionRadiusSquared);
            if (goodSquare && rc.canSenseLocation(newLoc) && rc.senseRubble(newLoc) < 5) mediocreDir = dir;
            goodSquare &= (rc.canSenseLocation(newLoc)) && (rc.senseRubble(newLoc) == 0);
            if (goodSquare && rc.canBuildRobot(RobotType.LABORATORY, dir)) {
                builtLab = true;
                labLocs.add(me.add(dir));
                writeMisc(BIT_LAB, readMisc(BIT_LAB, NUM_LAB) + 1, NUM_LAB);
                rc.buildRobot(RobotType.LABORATORY, dir);
            }
        }
        if (mediocreDir != null && rc.canBuildRobot(RobotType.LABORATORY, mediocreDir)) {
            builtLab = true;
            labLocs.add(me.add(mediocreDir));
            writeMisc(BIT_LAB, readMisc(BIT_LAB, NUM_LAB) + 1, NUM_LAB);
            rc.buildRobot(RobotType.LABORATORY, mediocreDir);
        }
        Direction dir = me.directionTo(nearestCorner);
        if (me.distanceSquaredTo(nearestCorner) <= 2) {
            Direction bestDir = dir;
            Direction newDir;
            int rubble = 100;
            int newRubble;
            if (rc.canSenseLocation(me.add(dir)) && rc.canBuildRobot(RobotType.LABORATORY, dir)) rubble = rc.senseRubble(me.add(dir));
            newDir = dir.rotateLeft();
            if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(RobotType.LABORATORY, newDir)) {
                newRubble = rc.senseRubble(me.add(newDir));
                if (newRubble < rubble) {
                    bestDir = newDir;
                    rubble = newRubble;
                }
            }
            newDir = dir.rotateRight();
            if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(RobotType.LABORATORY, newDir)) {
                newRubble = rc.senseRubble(me.add(newDir));
                if (newRubble < rubble) {
                    bestDir = newDir;
                    rubble = newRubble;
                }
            }

            newDir = dir.rotateLeft().rotateLeft();
            if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(RobotType.LABORATORY, newDir)) {
                newRubble = rc.senseRubble(me.add(newDir));
                if (newRubble < rubble) {
                    bestDir = newDir;
                    rubble = newRubble;
                }
            }
            newDir = dir.rotateRight().rotateRight();
            if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(RobotType.LABORATORY, newDir)) {
                newRubble = rc.senseRubble(me.add(newDir));
                if (newRubble < rubble) {
                    bestDir = newDir;
                    rubble = newRubble;
                }
            }

            if (rc.canBuildRobot(RobotType.LABORATORY, bestDir)) {
                builtLab = true;
                labLocs.add(me.add(bestDir));
                rc.buildRobot(RobotType.LABORATORY, bestDir);
                writeMisc(BIT_LAB, readMisc(BIT_LAB, NUM_LAB) + 1, NUM_LAB);
            }
        } else {
            dir = bestMove(dir);
            if (rc.canMove(dir)) rc.move(dir);
        }
    }
}
