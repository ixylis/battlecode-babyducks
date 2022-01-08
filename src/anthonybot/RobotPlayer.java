package anthonybot;

import battlecode.common.*;
import java.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

  /**
   * We will use this variable to count the number of turns this robot has been alive.
   * You can use static variables like this to save any information you want. Keep in mind that even though
   * these variables are static, in Battlecode they aren't actually shared between your robots.
   */
  static int turnCount = 0;
  static MapLocation nearestLead = null;
  static MapLocation target = null;
  static MapLocation hqLoc = null;
  static MapLocation enemyHqLoc = null;
  static int minersBuilt = 0;

  static final int initialMiners = 10; // miners to build initially
  static final int maxLead = 1000; // don't build miners if we have plenty of lead

  /**
   * A random number generator.
   * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
   * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
   * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
   */
  static final Random rng = new Random(6147);

  /** Array containing all the possible movement directions. */
  static final Direction[] directions = {
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST,
  };

  /**
   * run() is the method that is called when a robot is instantiated in the Battlecode world.
   * It is like the main function for your robot. If this method returns, the robot dies!
   *
   * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
   *      information on its current status. Essentially your portal to interacting with the world.
   **/
  @SuppressWarnings("unused")
  public static void run(RobotController rc) throws GameActionException {

    // Hello world! Standard output is very useful for debugging.
    // Everything you say here will be directly viewable in your terminal when you run a match!
    System.out.println("I'm a " + rc.getType() + " and I just got created! I have health " + rc.getHealth());

    // You can also use indicators to save debug notes in replays.
    rc.setIndicatorString("Hello world!");

    while (true) {
      // This code runs during the entire lifespan of the robot, which is why it is in an infinite
      // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
      // loop, we call Clock.yield(), signifying that we've done everything we want to do.

      turnCount += 1;  // We have now been alive for one more turn!
      System.out.println("Age: " + turnCount + "; Location: " + rc.getLocation());

      // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
      try {
        // The same run() function is called for every robot on your team, even if they are
        // different types. Here, we separate the control depending on the RobotType, so we can
        // use different strategies on different robots. If you wish, you are free to rewrite
        // this into a different control structure!
        switch (rc.getType()) {
          case ARCHON:   runArchon(rc);  break;
          case MINER:    runMiner(rc);   break;
          case SOLDIER:  runSoldier(rc); break;
          case LABORATORY: // Examplefuncsplayer doesn't use any of these robot types below.
          case WATCHTOWER: // You might want to give them a try!
          case BUILDER:
          case SAGE:     break;
        }
      } catch (GameActionException e) {
        // Oh no! It looks like we did something illegal in the Battlecode world. You should
        // handle GameActionExceptions judiciously, in case unexpected events occur in the game
        // world. Remember, uncaught exceptions cause your robot to explode!
        System.out.println(rc.getType() + " Exception");
        e.printStackTrace();

      } catch (Exception e) {
        // Oh no! It looks like our code tried to do something bad. This isn't a
        // GameActionException, so it's more likely to be a bug in our code.
        System.out.println(rc.getType() + " Exception");
        e.printStackTrace();

      } finally {
        // Signify we've done everything we want to do, thereby ending our turn.
        // This will make our code wait until the next turn, and then perform this loop again.
        Clock.yield();
      }
      // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
    }

    // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
  }

  /**
   * Run a single turn for an Archon.
   * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
   */
  static void runArchon(RobotController rc) throws GameActionException {
    // build soldiers if under attack
    RobotInfo [] robots = rc.senseNearbyRobots(RobotType.ARCHON.visionRadiusSquared, rc.getTeam().opponent());
    for (RobotInfo robot : robots) {
      if (robot.type == RobotType.SOLDIER) {
        for (Direction dir : directions) {
          if (rc.canBuildRobot(RobotType.SOLDIER, dir)) rc.buildRobot(RobotType.SOLDIER, dir);
        }
      }
    }
    // if we have multiple archons, some shouldn't build unless we have surplus wealth
    if (rc.getArchonCount() > 1 && rc.getTeamLeadAmount(rc.getTeam()) < 150) {
      if (rng.nextInt(rc.getArchonCount()) != 0) return;
    }
    // Pick a direction to build in.
    for (Direction dir : directions) {
      if (rc.getTeamLeadAmount(rc.getTeam()) < maxLead && (minersBuilt < initialMiners || rng.nextBoolean())) {
        // Let's try to build a miner.
        // Only build miners if we can also afford a soldier (otherwise we never build soldiers)
        rc.setIndicatorString("Trying to build a miner");
        if (rc.canBuildRobot(RobotType.SOLDIER, dir) || (rc.canBuildRobot(RobotType.MINER, dir) && minersBuilt < initialMiners)) {
          rc.buildRobot(RobotType.MINER, dir);
          minersBuilt ++;
        }
      } else {
        // Let's try to build a soldier.
        rc.setIndicatorString("Trying to build a soldier");
        if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
          rc.buildRobot(RobotType.SOLDIER, dir);
        }
      }
    }
  }

  /**
   * Run a single turn for a Miner.
   * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
   */
  static void runMiner(RobotController rc) throws GameActionException {
    if (hqLoc == null) {
      RobotInfo [] robots = rc.senseNearbyRobots(2, rc.getTeam());
      for (RobotInfo robot : robots)
        if (robot.type == RobotType.ARCHON)
          hqLoc = robot.location;
    }
    // Try to mine on squares around us.
    MapLocation me = rc.getLocation();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
        if (!rc.canSenseLocation(mineLocation)) continue;
        // Notice that the Miner's action cooldown is very low.
        // You can mine multiple times per turn!
        while (rc.canMineGold(mineLocation)) {
          rc.mineGold(mineLocation);
        }
        // leave behind 1 lead (to regenerate)
        while (rc.canMineLead(mineLocation) && rc.senseLead(mineLocation) > 1) {
          rc.mineLead(mineLocation);
        }
      }
    }

    // run away from nearby soldiers
    /*
    RobotInfo [] robots = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam().opponent());
    for (RobotInfo robot : robots) {
      if (robot.type == RobotType.SOLDIER) {
        // run away
        Direction dir = robot.location.directionTo(me);
        tryMoveImproved(rc, dir);
      }
    }
    */

    // try to find nearest lead
    if (nearestLead == null) {
      for (int n = 1; n < 3; n ++) {
        nearestLead = findLead(rc, n);
        if (nearestLead != null) {
          target = null;
          break;
        }
      }
    }

    if (nearestLead != null && rc.canSenseLocation(nearestLead) && rc.senseLead(nearestLead) <= 1) nearestLead = null;

    if (nearestLead != null) {
      Direction dir = me.directionTo(nearestLead);
      tryMoveImproved(rc, dir);
    }

    // try to move away from HQ
    if (hqLoc != null && me.distanceSquaredTo(hqLoc) < 9) {
      target = null;
      Direction dir = hqLoc.directionTo(me);
      tryMoveImproved(rc, dir);
    }
    
    // pick a random target on the map and go there
    if (target == null) {
      target = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
    }
    if (me.distanceSquaredTo(target) < 9) target = null;
    if (target != null) {
      Direction dir = me.directionTo(target);
      tryMoveImproved(rc, dir);
    }

    // Also try to move randomly.
    moveRandom(rc);
  }

  // find lead within n squares of robot
  static MapLocation findLead(RobotController rc, int n) throws GameActionException {
    MapLocation me = rc.getLocation();
    for (int dx = -n; dx <= n; dx ++) {
      for (int dy = -n; dy <= n; dy ++) {
        MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
        if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 1) return mineLocation;
      }
    }
    return null;
  }

  /**
   * Run a single turn for a Soldier.
   * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
   */
  static void runSoldier(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    if (hqLoc == null) {
      RobotInfo [] robots = rc.senseNearbyRobots(2, rc.getTeam());
      for (RobotInfo robot : robots)
        if (robot.type == RobotType.ARCHON)
          hqLoc = robot.location;
    }
    // Try to attack someone
    int radius = rc.getType().actionRadiusSquared;
    Team opponent = rc.getTeam().opponent();
    RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
    if (enemies.length > 0) {
      MapLocation toAttack = enemies[0].location;
      if (rc.canAttack(toAttack))
        rc.attack(toAttack);
    }

    radius = rc.getType().visionRadiusSquared;
    enemies = rc.senseNearbyRobots(radius, opponent);
    RobotInfo [] friends = rc.senseNearbyRobots(radius, rc.getTeam());
    if (enemies.length > 0) {
      if (friends.length > enemies.length) {
        target = null;
        MapLocation newTarget = enemies[0].location;
        Direction dir = me.directionTo(newTarget);
        tryMoveImproved(rc, dir);
        // try to attack (maybe we've moved into range)
        if (rc.canAttack(newTarget))
          rc.attack(newTarget);
      }
      // run away from soldiers and toward miners
      for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.SOLDIER) {
          Direction dir = enemy.location.directionTo(me);
          tryMoveImproved(rc, dir);
        }
      }
      for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.MINER) {
          Direction dir = me.directionTo(enemy.location);
          tryMoveImproved(rc, dir);
        }
      }
    }

    // try to find enemy HQ
    int enemyHqLocCode = rc.readSharedArray(0);
    if (enemyHqLoc == null) {
      // read from shared array
      if ((enemyHqLocCode & 0x1FFF) != 0) {
        int x = (enemyHqLocCode >> 6) & 0x3F;
        int y = enemyHqLocCode & 0x3F;
        enemyHqLoc = new MapLocation(x, y);
      } else {
        for (RobotInfo enemy : enemies) {
          if (enemy.team == opponent && enemy.type == RobotType.ARCHON) {
            enemyHqLoc = enemy.location;
            int x = enemyHqLoc.x;
            int y = enemyHqLoc.y;
            enemyHqLocCode = (1 << 12) | (x << 6) | y;
            rc.writeSharedArray(0, enemyHqLocCode);
          }
        }
      }
    } else {
      if ((enemyHqLocCode & 0x1FFF) == 0) enemyHqLoc = null;
      else if (rc.canSenseLocation(enemyHqLoc)) {
        RobotInfo enemyHq = rc.senseRobotAtLocation(enemyHqLoc);
        if (enemyHq == null || enemyHq.team == rc.getTeam() || enemyHq.type != RobotType.ARCHON) rc.writeSharedArray(0, 0);
      } else {
        // move toward enemy HQ if we're surrounded by more friends than enemies
        if (friends.length > enemies.length) {
          Direction dir = me.directionTo(enemyHqLoc);
          tryMoveImproved(rc, dir);
        }
      }
    }

    // try to move away from HQ
    if (hqLoc != null && me.distanceSquaredTo(hqLoc) < 9) {
      target = null;
      Direction dir = hqLoc.directionTo(me);
      tryMoveImproved(rc, dir);
    }

    // pick a random target on the map and go there
    if (target == null) {
      target = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
    }
    if (me.distanceSquaredTo(target) < 9) target = null;
    if (target != null) {
      Direction dir = me.directionTo(target);
      tryMoveImproved(rc, dir);
    }

    // Also try to move randomly.
    moveRandom(rc);
  }

  static void tryMove(RobotController rc, Direction dir) throws GameActionException {
    if (rc.canMove(dir)) rc.move(dir);
  }

  static void tryMoveImproved(RobotController rc, Direction dir) throws GameActionException {
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
    rc.move(bestDir);
  }

  static void moveRandom(RobotController rc) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    tryMoveImproved(rc, dir);
  }

  // move to a square weighted by exp(-rubble * beta)
  static void moveWeightedRandom(RobotController rc, double beta) throws GameActionException {
    MapLocation me = rc.getLocation();
    // compute partition function
    double Z = 0;
    for (Direction dir : directions) {
      if (rc.canMove(dir)) {
        MapLocation newLoc = me.add(dir);
        Z += Math.exp(-rc.senseRubble(newLoc) * beta);
      }
    }
    Z *= rng.nextDouble();
    double cumsum = 0;
    for (Direction dir : directions) {
      if (rc.canMove(dir)) {
        MapLocation newLoc = me.add(dir);
        cumsum += Math.exp(-rc.senseRubble(newLoc) * beta);
        if (cumsum > Z) rc.move(dir);
      }
    }

  }

}
