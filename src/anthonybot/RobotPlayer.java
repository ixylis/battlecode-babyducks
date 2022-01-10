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
          case LABORATORY: 
          case WATCHTOWER: runWatchtower(rc); break;
          case BUILDER:  runBuilder(rc); break;
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

  static void runBuilder(RobotController rc) throws GameActionException {
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
      target = null;
      Direction dir = hqLoc.directionTo(me);
      tryMoveImproved(rc, dir);
    }


    // build watchtowers, but only at least 3 squares away from HQ (to avoid spawn locking)
    if (me.distanceSquaredTo(hqLoc) >= 25 && rc.getTeamLeadAmount(rc.getTeam()) > maxLead) {
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

  static void runWatchtower(RobotController rc) throws GameActionException {
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

  static boolean spawn(RobotController rc, RobotType type, Direction preferredDir) throws GameActionException {
    Direction dir = preferredDir;
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.rotateLeft();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.rotateRight();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.rotateLeft().rotateLeft();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.rotateRight().rotateRight();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.opposite().rotateRight();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.opposite().rotateLeft();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    dir = preferredDir.opposite();
    if (rc.canBuildRobot(type, dir)) {rc.buildRobot(type, dir); return true;}
    return false;
  }

  // broadcast HQ location
  static void broadcastLocation(RobotController rc, MapLocation loc) throws GameActionException {
    int arrayIndex = 60;
    while (arrayIndex < 64 && rc.readSharedArray(arrayIndex) != 0) arrayIndex ++;
    rc.writeSharedArray(arrayIndex, (1 << 12) | (loc.x << 6) | loc.y);
  }

  /**
   * Run a single turn for an Archon.
   * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
   */
  static void runArchon(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    if (turnCount == 1) broadcastLocation(rc, me);
    // compute preferred direction for spawning
    Direction preferredSoldier = me.directionTo(new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2));
    nearestLead = null;
    for (int n = 1; n < 5; n ++) {
      nearestLead = findLead(rc, n);
      if (nearestLead != null) {
        break;
      }
    }
    Direction preferredMiner = preferredSoldier;
    if (nearestLead != null) preferredMiner = me.directionTo(nearestLead);
    // build soldiers if under attack
    RobotInfo [] robots = rc.senseNearbyRobots(RobotType.ARCHON.visionRadiusSquared, rc.getTeam().opponent());
    for (RobotInfo robot : robots) {
      if (robot.type == RobotType.SOLDIER) {
        spawn(rc, RobotType.SOLDIER, me.directionTo(robot.location));
      }
    }
    // if there are few units and little lead around HQ, spawn a builder to suicide into lead
    int numLead = 0;
    for (int dx = -5; dx <= 5; dx ++) {
      for (int dy = -5; dy <= 5; dy ++) {
        MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
        if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) > 0) numLead ++;
      }
    }
    if (turnCount > 100 && rc.senseNearbyRobots().length < 5 && rc.getTeamLeadAmount(rc.getTeam()) < 100) {
      if (numLead < 20) {
        for (Direction dir : directions)
          if (((rc.canSenseLocation(me.add(dir)) && rc.senseLead(me.add(dir)) == 0)
              || (rc.canSenseLocation(me.add(dir).add(dir)) && rc.senseLead(me.add(dir).add(dir)) == 0))
              && rc.canBuildRobot(RobotType.BUILDER, dir))
            rc.buildRobot(RobotType.BUILDER, dir);
      }
    }
    // if we have multiple archons, some shouldn't build unless we have surplus wealth
    if (rc.getArchonCount() > 1 && rc.getTeamLeadAmount(rc.getTeam()) < 150) {
      if (rng.nextInt(rc.getArchonCount()) != 0) return;
    }
    robots = rc.senseNearbyRobots(RobotType.ARCHON.visionRadiusSquared, rc.getTeam());
    int numBuilders = 0;
    int numMiners = 0;
    for (RobotInfo robot : robots) {
      if (robot.type == RobotType.BUILDER) numBuilders ++;
      if (robot.type == RobotType.MINER) numMiners ++;
    }
    if (numLead > 4 * numMiners && rc.getTeamLeadAmount(rc.getTeam()) < 1000)
      spawn(rc, RobotType.MINER, preferredMiner);
    // build builders if we have infinity lead
    if (rc.getTeamLeadAmount(rc.getTeam()) > maxLead) {
      if (rc.canBuildRobot(RobotType.BUILDER, preferredSoldier)) rc.buildRobot(RobotType.BUILDER, preferredSoldier);
      if (rc.getTeamLeadAmount(rc.getTeam()) > maxLead * (numBuilders + 1))
        spawn(rc, RobotType.BUILDER, preferredSoldier);
    }
    // build miners until we see an enemy unit, after which build mostly soldiers
    if (rc.readSharedArray(1) != 0 && rng.nextInt(5) != 0) {
      spawn(rc, RobotType.SOLDIER, preferredSoldier);
    } else {
      spawn(rc, RobotType.MINER, preferredMiner);
    }
  }

  // check possible enemy HQ locations
  // TODO: Reuse some of this from turn to turn
  static void scoutHqReflections (RobotController rc) throws GameActionException {
    int height = rc.getMapHeight() - 1;
    int width = rc.getMapWidth() - 1;
    for (int index = 60; index < 64; index ++) {
      if (rc.readSharedArray(index) != 0) {
        int value = rc.readSharedArray(index);
        int x = (value >> 6) & 0x3F;
        int y = value & 0x3F;
        if (((value >> 13) & 1) == 0) {
          MapLocation possibleEnemyHq = new MapLocation(x, height - y); // vertical reflection
          if (rc.canSenseLocation(possibleEnemyHq)) {
            RobotInfo putativeEnemyHq = rc.senseRobotAtLocation(possibleEnemyHq);
            if (putativeEnemyHq == null || putativeEnemyHq.team == rc.getTeam() || putativeEnemyHq.type != RobotType.ARCHON)
              rc.writeSharedArray(index, value | (1 << 13));
          }
        }
        if (((value >> 14) & 1) == 0) {
          MapLocation possibleEnemyHq = new MapLocation(width - x, height); // horizontal reflection
          if (rc.canSenseLocation(possibleEnemyHq)) {
            RobotInfo putativeEnemyHq = rc.senseRobotAtLocation(possibleEnemyHq);
            if (putativeEnemyHq == null || putativeEnemyHq.team == rc.getTeam() || putativeEnemyHq.type != RobotType.ARCHON)
              rc.writeSharedArray(index, value | (1 << 14));
          }
        }
        if (((value >> 15) & 1) == 0) {
          MapLocation possibleEnemyHq = new MapLocation(width - x, height - y); // rotation
          if (rc.canSenseLocation(possibleEnemyHq)) {
            RobotInfo putativeEnemyHq = rc.senseRobotAtLocation(possibleEnemyHq);
            if (putativeEnemyHq == null || putativeEnemyHq.team == rc.getTeam() || putativeEnemyHq.type != RobotType.ARCHON)
              rc.writeSharedArray(index, value | (1 << 15));
          }
        }
        // if two of these are nonzero, then the third is the HQ location
        if (enemyHqLoc == null) {
          if (((value >> 13) & 1) == 1 && ((value >> 14) & 1) == 1 && ((value >> 15) & 1) == 0) {
            enemyHqLoc = new MapLocation(width - x, height - y);
            rc.writeSharedArray(0, (1 << 12) | ((width - x) << 6) | (height - y));
          } else if (((value >> 13) & 1) == 1 && ((value >> 14) & 1) == 0 && ((value >> 15) & 1) == 1) {
            enemyHqLoc = new MapLocation(width - x, height);
            rc.writeSharedArray(0, (1 << 12) | ((width - x) << 6) | (height));
          } else if (((value >> 13) & 1) == 0 && ((value >> 14) & 1) == 1 && ((value >> 15) & 1) == 1) {
            enemyHqLoc = new MapLocation(x, height - y);
            rc.writeSharedArray(0, (1 << 12) | (x << 6) | (height - y));
          }
        }
      }
    }
  }

  /**
   * Run a single turn for a Miner.
   * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
   */
  static void runMiner(RobotController rc) throws GameActionException {
    scoutHqReflections(rc);
    // reset target every 128 turns
    if ((turnCount & 0x7F) == 0) { target = null; nearestLead = null;}
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

    // scout for enemy units
    if (rc.readSharedArray(1) == 0) {
      RobotInfo [] robots = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam().opponent());
      if (robots.length > 0) rc.writeSharedArray(1, 1);
    }

    // try to find nearest lead
    if (nearestLead == null) {
      for (int n = 1; n < 4; n ++) {
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
      if (me.distanceSquaredTo(nearestLead) > rc.getType().actionRadiusSquared
          || rc.canMove(dir) && rc.senseRubble(me) >= rc.senseRubble(me.add(dir)))
        tryMoveImproved(rc, dir);
      else
        return;
    }

    // try to move away from HQ
    if (hqLoc != null && me.distanceSquaredTo(hqLoc) < 9) {
      target = null;
      Direction dir = hqLoc.directionTo(me);
      tryMoveImproved(rc, dir);
    }
    
    // if you're adjacent to a lead deposit and no other miners are around, stay there until lead regenerates
    // TODO: Maybe only do this if there's >1 lead nearby
    for (int dx = -1; dx <= 1; dx ++) {
      for (int dy = -1; dy <= 1; dy ++) {
        MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
        if (rc.canSenseLocation(mineLocation) && rc.senseLead(mineLocation) >= 1) {
          boolean nearbyMiner = false;
          for (RobotInfo robot : rc.senseNearbyRobots(mineLocation, 2, rc.getTeam())) {
            if (robot.type == RobotType.MINER) {
              nearbyMiner = true;
              break;
            }
          }
          if (!nearbyMiner) return;
        }
      }
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
    scoutHqReflections(rc);
    boolean attacking = false;
    // reset target every 128 turns
    if ((turnCount & 0x7F) == 0) target = null;
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
      attacking = true;
      MapLocation toAttack = enemies[0].location;
      if (rc.canAttack(toAttack))
        rc.attack(toAttack);
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
      }
    }

    radius = rc.getType().visionRadiusSquared;
    enemies = rc.senseNearbyRobots(radius, opponent);
    RobotInfo [] friends = rc.senseNearbyRobots(radius, rc.getTeam());
    if (enemies.length > 0) {
      if (friends.length > enemies.length) {
        target = null;
        MapLocation newTarget = enemies[0].location;
        Direction dir = me.directionTo(newTarget);
        if (me.distanceSquaredTo(newTarget) > rc.getType().actionRadiusSquared
            || rc.canMove(dir) && rc.senseRubble(me) >= rc.senseRubble(me.add(dir)))
          tryMoveImproved(rc, dir);
        else
          return;
        // try to attack (maybe we've moved into range)
        if (rc.canAttack(newTarget))
          rc.attack(newTarget);
      }
      // run away from soldiers and toward miners
      for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.SOLDIER) {
          Direction dir = enemy.location.directionTo(me);
          if (rc.senseRubble(me) >= rc.senseRubble(me.add(dir)))
            tryMoveImproved(rc, dir);
          else
            return;
        }
      }
      for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.MINER) {
          MapLocation newTarget = enemy.location;
          Direction dir = me.directionTo(newTarget);
          if (me.distanceSquaredTo(newTarget) > rc.getType().actionRadiusSquared
              || rc.canMove(dir) && rc.senseRubble(me) >= rc.senseRubble(me.add(dir))) {
            tryMoveImproved(rc, dir);
            if (rc.canAttack(newTarget))
              rc.attack(newTarget);
          } else {
            return;
          }
        }
      }
    }

    if (enemyHqLoc != null && !rc.canSenseLocation(enemyHqLoc)) {
      // move toward enemy HQ if we're surrounded by more friends than enemies
      if (friends.length > enemies.length) {
        Direction dir = me.directionTo(enemyHqLoc);
        tryMoveImproved(rc, dir);
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
    if (rc.canMove(bestDir))
      rc.move(bestDir);
  }

  static void moveRandom(RobotController rc) throws GameActionException {
    rc.setIndicatorString("Moved randomly!");
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
