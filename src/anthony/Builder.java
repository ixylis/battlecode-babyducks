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
        }
      }
      // go to corner of board and build a lab
      MapLocation nearestCorner = corners[0];
      for (MapLocation corner : corners) {
        if (me.distanceSquaredTo(corner) < me.distanceSquaredTo(nearestCorner))
          nearestCorner = corner;
      }
      if (me.distanceSquaredTo(nearestCorner) <= 2) {
        Direction dir = me.directionTo(nearestCorner);
        if (rc.canBuildRobot(RobotType.LABORATORY, dir)) {
          rc.buildRobot(RobotType.LABORATORY, dir);
          rc.writeSharedArray(INDEX_LAB, 0);
        }
      } else {
        Direction dir = me.directionTo(nearestCorner);
        dir = bestMove(dir);
        if (rc.canMove(dir)) rc.move(dir);
      }
    }
}
