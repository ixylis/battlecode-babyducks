package sprintref;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

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
      // suicide mission
      if (me.distanceSquaredTo(hqLoc) <= 32 && rc.getTeamLeadAmount(rc.getTeam()) < 100 && rc.senseLead(me) == 0)
        rc.disintegrate();

      // try to move away from HQ
      if (!nearbyBuilding && hqLoc != null && me.distanceSquaredTo(hqLoc) < 25) {
        Direction dir = hqLoc.directionTo(me);
        tryMoveImproved(rc, dir);
      }


      // build watchtowers, but only at least 3 squares away from HQ (to avoid spawn locking)
      if (me.distanceSquaredTo(hqLoc) >= 25 && rc.getTeamLeadAmount(rc.getTeam()) > MAX_LEAD) {
        //rc.setIndicatorString("Trying to build a watchtower!");
        for (Direction dir : directions)
          if (rc.canBuildRobot(RobotType.WATCHTOWER, dir) && ((me.add(dir).x + me.add(dir).y) & 1) == 0)
            rc.buildRobot(RobotType.WATCHTOWER, dir);
      } else {
        rc.setIndicatorString("Not trying to build a watchtower!");
      }

      if (!nearbyBuilding) {
        moveRandom(rc);
      }
    }

    void tryMoveImproved(RobotController rc, Direction dir) throws GameActionException {
      double penalty = 2.0; // for moving 45-degree rotation from target
      MapLocation me = rc.getLocation();
      Direction bestDir = null;
      double moveCost = Double.MAX_VALUE;
      if (rc.canMove(dir)) {
        if (rc.senseRubble(me.add(dir)) < 20) {
          rc.move(dir);
          return;
        } else {
          bestDir = dir;
          moveCost = 1 + rc.senseRubble(me.add(dir)) / 20.0;
        }
      }
      Direction newDir = dir.rotateLeft();
      if (rc.canMove(newDir)) {
        double newCost = (1 + rc.senseRubble(me.add(newDir)) / 20.0) * penalty;
        if (newCost < moveCost) {
          moveCost = newCost;
          bestDir = newDir;
        }
      }
      newDir = dir.rotateRight();
      if (rc.canMove(newDir)) {
        double newCost = (1 + rc.senseRubble(me.add(newDir)) / 20.0) * penalty;
        if (newCost < moveCost) {
          moveCost = newCost;
          bestDir = newDir;
        }
      }
      if (rc.canMove(bestDir))
        rc.move(bestDir);
    }
    void moveRandom(RobotController rc) throws GameActionException {
      rc.setIndicatorString("Moved randomly!");
      Direction dir = directions[rng.nextInt(directions.length)];
      tryMoveImproved(rc, dir);
    }
}
