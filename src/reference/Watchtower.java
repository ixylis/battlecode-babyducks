package reference;

import battlecode.common.*;

public class Watchtower extends Robot {
    Watchtower(RobotController r) throws GameActionException {
        super(r);
    }
    public void turn() throws GameActionException {
      // Try to attack someone
      int radius = rc.getType().actionRadiusSquared;
      Team opponent = rc.getTeam().opponent();
      RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
      if (enemies.length > 0) {
        MapLocation toAttack = enemies[0].location;
        if (rc.canAttack(toAttack))
          rc.attack(toAttack);
      }
    }
}
