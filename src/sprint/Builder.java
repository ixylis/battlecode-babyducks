package sprint;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Builder extends Robot {
    Builder(RobotController r) throws GameActionException {
        super(r);
    }
    private MapLocation hqLoc = null;
    private MapLocation labLoc = null;
    private boolean builtLab = false;
    public void turn() throws GameActionException {
      rc.setIndicatorString("Cooldown: " + rc.getActionCooldownTurns());
      MapLocation me = rc.getLocation();
      if (hqLoc == null) {
        RobotInfo [] robots = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo robot : robots) {
          if (robot.type == RobotType.ARCHON) {
            hqLoc = robot.location;
            labLoc = hqLoc; // initialize to this until we actually build a lab
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
      // go to corner of board and build a lab
      MapLocation nearestCorner = corners[0];
      for (MapLocation corner : corners) {
        if ((me.distanceSquaredTo(corner) < me.distanceSquaredTo(nearestCorner) 
              || hqLoc.distanceSquaredTo(nearestCorner) < RobotType.LABORATORY.visionRadiusSquared
              || labLoc.distanceSquaredTo(nearestCorner) < RobotType.LABORATORY.visionRadiusSquared) 
            && (hqLoc.distanceSquaredTo(corner) > RobotType.LABORATORY.visionRadiusSquared
              && labLoc.distanceSquaredTo(corner) > RobotType.LABORATORY.visionRadiusSquared))
          nearestCorner = corner;
      }
      Direction dir = me.directionTo(nearestCorner);
      if (me.distanceSquaredTo(nearestCorner) <= 2 || 
          (!builtLab && me.distanceSquaredTo(hqLoc) > 9
           && (rc.canSenseLocation(me.add(dir)) && rc.senseRubble(me.add(dir)) == 0
             || (rc.canSenseLocation(me.add(dir.rotateLeft())) && rc.senseRubble(me.add(dir.rotateLeft())) == 0)
             || (rc.canSenseLocation(me.add(dir.rotateRight())) && rc.senseRubble(me.add(dir.rotateRight())) == 0)))) {
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
          labLoc = me.add(bestDir);
          rc.buildRobot(RobotType.LABORATORY, bestDir);
          writeMisc(BIT_LAB, readMisc(BIT_LAB, NUM_LAB) + 1, NUM_LAB);
        }
      } else {
        dir = bestMove(dir);
        if (rc.canMove(dir)) rc.move(dir);
      }
    }
}
