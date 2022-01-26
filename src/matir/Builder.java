package matir;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Builder extends Robot {
    Builder(RobotController r) throws GameActionException {
        super(r);
    }
    private MapLocation hqLoc = null;
    public void turn() throws GameActionException {
      writeMisc(BIT_BUILDER, readMisc(BIT_BUILDER, NUM_BUILDER) + 1, NUM_BUILDER);
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
      RobotInfo [] friends = rc.senseNearbyRobots(
              BUILDER.visionRadiusSquared, rc.getTeam());
      for (RobotInfo robot : friends) {
        if (robot.health < robot.type.getMaxHealth(robot.level)) {
          if (rc.canRepair(robot.location)) rc.repair(robot.location);
           else if (robot.type.isBuilding()) {
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
          if (rc.canMove(dir) && rc.senseRubble(newLoc) < rubble &&
                  newLoc.distanceSquaredTo(buildingToRepair) <
          BUILDER.actionRadiusSquared)
            rc.move(dir);
        }
        return;
      }
      // go to corner of board and build a lab
      MapLocation nearestCorner = corners[0];
      for (MapLocation corner : corners) {
        if (me.distanceSquaredTo(corner) < me.distanceSquaredTo(nearestCorner)
              || (hqLoc.distanceSquaredTo(nearestCorner) < LABORATORY.visionRadiusSquared
            && hqLoc.distanceSquaredTo(corner) > LABORATORY.visionRadiusSquared))
          nearestCorner = corner;
      }
      Direction dir = me.directionTo(nearestCorner);
      if (me.distanceSquaredTo(nearestCorner) <= LABORATORY.visionRadiusSquared ||
          me.distanceSquaredTo(hqLoc) > LABORATORY.visionRadiusSquared) {
        Direction bestDir = dir;
        Direction newDir;
        int rubble = 100;
        int newRubble;
        if (rc.canSenseLocation(me.add(dir)) && rc.canBuildRobot(LABORATORY, dir)) rubble = rc.senseRubble(me.add(dir));
        newDir = dir.rotateLeft();
        if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(LABORATORY, newDir)) {
          newRubble = rc.senseRubble(me.add(newDir));
          if (newRubble < rubble) {
            bestDir = newDir;
            rubble = newRubble;
          }
        }
        newDir = dir.rotateRight();
        if (rc.canSenseLocation(me.add(newDir)) && rc.canBuildRobot(LABORATORY, newDir)) {
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
          rc.buildRobot(RobotType.LABORATORY, bestDir);
        }
      } else {
        dir = bestMove(dir);
        if (rc.canMove(dir)) rc.move(dir);
      }
    }
}
