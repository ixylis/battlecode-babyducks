package matir;

import battlecode.common.*;

import static battlecode.common.RobotType.*;

public class Builder extends Robot {

    MapLocation target;

    Builder(RobotController r) throws GameActionException {
        super(r);
    }

    private MapLocation hqLoc = null;

    public void turn() throws GameActionException {
        writeMisc(BIT_BUILDER, readMisc(BIT_BUILDER, NUM_BUILDER) + 1, NUM_BUILDER);
        MapLocation me = rc.getLocation();
        if (hqLoc == null) {
            RobotInfo[] robots = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    hqLoc = robot.location;
                }
            }
        }
        // if we can repair a building, do that
        boolean nearbyBuilding = false;
        MapLocation buildingToRepair = null;
        RobotInfo[] friends = rc.senseNearbyRobots(
                BUILDER.visionRadiusSquared, rc.getTeam());
        for (RobotInfo robot : friends) {
            if (robot.health < robot.type.getMaxHealth(robot.level)) {
                if (rc.canRepair(robot.location)) rc.repair(robot.location);
                else if (robot.type.isBuilding()) {
                    buildingToRepair = robot.location;
                    nearbyBuilding = true;
                    break;
                }
            }
        }
        if (target == null) {
            target = corners[0];
            for (MapLocation corner : corners) {
                if (me.distanceSquaredTo(corner) <
                        me.distanceSquaredTo(target))
                    target = corner;
            }
        }
        MapLocation sol = getNearestEnemyChunk();
        if (sol != null) {
            if (sol.distanceSquaredTo(myLoc) <= 25) {
                Direction moveDir = sol.directionTo(me);
                target = me.add(moveDir);
                if(rc.onTheMap(target)) {
                    moveToward(target);
                    return;
                } else {
                    renewTarget();
                }
            }
        }

        Direction bestDir = null;
        for (Direction dir : directions) {
            MapLocation newDir = me.add(dir);
            if (!rc.canSenseLocation(newDir)) continue;
            if(rc.canSenseRobotAtLocation(newDir)) continue;

            if (bestDir == null) {
                bestDir = dir;
                continue;
            }

            if (rc.senseRubble(newDir) <
                    rc.senseRubble(me.add(bestDir)))
                bestDir = dir;
        }

        if (rc.canBuildRobot(RobotType.LABORATORY, bestDir)) {
            rc.buildRobot(RobotType.LABORATORY, bestDir);
            renewTarget();
        }

        if (nearbyBuilding) {
            moveToward(buildingToRepair);
        } else {
            if (target != null) {
                if (!rc.onTheMap(target) ||
                        myLoc.distanceSquaredTo(target) <= 13) {
                    renewTarget();
                }
                moveToward(target);
            } else {
                target = myLoc;
                renewTarget();
            }
        }

        rc.setIndicatorDot(target, 255, 255, 255);
    }

    private void renewTarget() throws GameActionException {
        MapLocation sol = getNearestEnemyChunk();
        target = corners[rng.nextInt(4)];
        if(sol != null) {
            if (rc.onTheMap(sol)) {
                for (MapLocation corner : corners) {
                    if (sol.distanceSquaredTo(corner) +
                            myLoc.distanceSquaredTo(corner) >
                            sol.distanceSquaredTo(target) +
                                    myLoc.distanceSquaredTo(target)
                            || (myLoc.distanceSquaredTo(target) < LABORATORY.visionRadiusSquared
                            && myLoc.distanceSquaredTo(corner) > LABORATORY.visionRadiusSquared))
                        target = corner;
                }
            }
        }
        rc.setIndicatorDot(myLoc, 0,0,0);
    }
}
