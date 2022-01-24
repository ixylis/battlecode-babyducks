package anthony;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Builder extends Robot {
    Builder(RobotController r) throws GameActionException {
        super(r);
    }
    private MapLocation hqLoc = null;
    public void turn() throws GameActionException {
      rc.setIndicatorString("Cooldown: " + rc.getActionCooldownTurns());
      MapLocation me = rc.getLocation();
      if (hqLoc == null) {
        RobotInfo [] robots = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo robot : robots)
          if (robot.type == RobotType.ARCHON)
            hqLoc = robot.location;
      }
      // if we can repair a building, do that
      boolean nearbyBuilding = false;
      RobotInfo [] friends = rc.senseNearbyRobots(5, rc.getTeam()); 
      for (RobotInfo robot : friends) {
        if (rc.canRepair(robot.location) && robot.health < robot.type.health) {
          rc.setIndicatorString("Repairing building");
          nearbyBuilding = true;
          rc.repair(robot.location);
          return;
        }
      }
      // go to corner of board and build a lab
      MapLocation nearestCorner = corners[0];
      for (MapLocation corner : corners) {
        if (me.distanceSquaredTo(corner) < me.distanceSquaredTo(nearestCorner))
          nearestCorner = corner;
      }
      Direction dir = me.directionTo(nearestCorner);
      if (me.distanceSquaredTo(nearestCorner) <= 2 || 
          (rc.senseNearbyRobots().length == 0 
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

        if (rc.canBuildRobot(RobotType.LABORATORY, bestDir)) {
          rc.buildRobot(RobotType.LABORATORY, bestDir);
          rc.writeSharedArray(INDEX_LAB, 0);
        }
      } else {
        dir = bestMove(dir);
        if (rc.canMove(dir)) rc.move(dir);
      }
    }
}
