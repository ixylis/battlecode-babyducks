package matir;

import battlecode.common.*;

import static battlecode.common.RobotType.*;
import static java.lang.Math.max;

public class Soldier extends Robot {
    Soldier(RobotController r) throws GameActionException {
        super(r);
    }

    private MapLocation movementTarget = null;

    public void turn() throws GameActionException {
        if (rc.isActionReady())
            attack();
        if (rc.isMovementReady())
            movement();
        else
            super.updateEnemySoliderLocations();
        if (rc.isActionReady()) attack();
        super.updateEnemyHQs();
        //rc.setIndicatorDot(Robot.intToLoc(rc.readSharedArray(INDEX_ENEMY_HQ+rc.getRoundNum()%4)), 190, 0, 190);
        rc.setIndicatorDot(intToChunk(rc.readSharedArray(INDEX_ENEMY_SOLDIER_LOCATION + rc.getRoundNum() % NUM_ENEMY_SOLDIER_CHUNKS)), 1, 255, 1);

    }

    /*
     * micro
     *
     * determine whether to attack, retreat, or hold.
     * imagine an advance:
     * every unit moves forward to the lowest rubble space toward enemy.
     * our strength = sum 1/(10+rubble) of all spaces with our units that have an enemy in range.
     * enemy strength is symmetric
     * if our strength > enemy strength, advance.
     * advance means move toward the enemy in a low rubble way.
     * look at the current position:
     * compute our strength and enemy strength
     * if enemy strength > our strength, retreat
     *
     * find which enemy units are already shooting this turn. if they already have something in their range, then
     *
     */
    private Direction lowestRubbleInDirectionFromLocation(MapLocation from, MapLocation target) throws GameActionException {
        Direction d = from.directionTo(target);
        MapLocation option1 = from.add(d);
        MapLocation option2 = from.add(d.rotateLeft());
        MapLocation option3 = from.add(d.rotateRight());
        int rubble1 = rc.canSenseLocation(option1) ? rc.senseRubble(option1) : 100;
        int rubble2 = rc.canSenseLocation(option2) ? rc.senseRubble(option2) : 100;
        int rubble3 = rc.canSenseLocation(option3) ? rc.senseRubble(option3) : 100;
        if (rubble1 <= rubble2 && rubble1 <= rubble3)
            return d;
        else if (rubble2 <= rubble3)
            return d.rotateLeft();
        else
            return d.rotateRight();
    }

    RobotInfo[] recentEnemies = new RobotInfo[10];
    int[] recentEnemiesRounds = new int[10];

    private boolean micro() throws GameActionException {
        //imagine the advance
        RobotInfo[] friends = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        if (enemies.length == 0)
            return false;
        boolean[][] toBeOccupied = new boolean[11][11];
        MapLocation nearest = null;
        for (RobotInfo r : enemies) {
            //if(r.type == RobotType.MINER) continue;
            if (nearest == null || rc.getLocation().distanceSquaredTo(nearest) > rc.getLocation().distanceSquaredTo(r.location))
                nearest = r.location;
        }
        int nearestInfDistance = max(Math.abs(nearest.x - rc.getLocation().x), Math.abs(nearest.y - rc.getLocation().y));

        /*
         * everyone moves forward one square toward the nearest enemy to my location.
         * for this purpose, we go one robot at a time, find the lowest rubble spot it can move to in that direction +/- 45 deg
         * declare that spot 'occupied by friendly forces'
         */
        Direction toMove = null;
        int myAdvanceStrength = 0;
        for (RobotInfo r : friends) {
            if (r.type != SOLDIER)
                continue;
            //find the nearest enemy in sight range of this friend
            MapLocation from = r.location;
            MapLocation to = null;
            int rubbleTo;
            Direction d = from.directionTo(nearest);
            MapLocation option1 = from.add(d);
            MapLocation option2 = from.add(d.rotateLeft());
            MapLocation option3 = from.add(d.rotateRight());
            int rubble0 = rc.senseRubble(from) * 2 + 10; // stay put if cost would more than double
            int rubble1 = rc.canSenseLocation(option1) ? rc.senseRubble(option1) : 100;
            int rubble2 = rc.canSenseLocation(option2) ? rc.senseRubble(option2) : 100;
            int rubble3 = rc.canSenseLocation(option3) ? rc.senseRubble(option3) : 100;
            if (!toBeOccupied[option1.x - rc.getLocation().x + 5][option1.y - rc.getLocation().y + 5] && rubble1 <= rubble2 && rubble1 <= rubble3 && rubble1 <= rubble0) {
                to = option1;
                rubbleTo = rubble1;
            } else if (!toBeOccupied[option2.x - rc.getLocation().x + 5][option2.y - rc.getLocation().y + 5] && rubble2 <= rubble3 && rubble2 <= rubble0) {
                to = option2;
                rubbleTo = rubble2;
            } else if (!toBeOccupied[option3.x - rc.getLocation().x + 5][option3.y - rc.getLocation().y + 5] && rubble3 <= rubble0) {
                to = option3;
                rubbleTo = rubble3;
            } else {
                rubbleTo = rc.senseRubble(from);
                to = from;
            }
            toBeOccupied[to.x - rc.getLocation().x + 5][to.y - rc.getLocation().y + 5] = true;
            if (max(Math.abs(to.x - rc.getLocation().x), Math.abs(to.y - rc.getLocation().y)) <= 3)
                myAdvanceStrength += 1000 / (10 + rubbleTo) * 10 / (10 + rc.senseRubble(r.location));
        }
        //now time to calculate the enemy strength
        int enemyAdvanceStrength = 0;
        for (RobotInfo r : enemies) {
            if (r.type == RobotType.MINER || r.type == RobotType.BUILDER || r.type == RobotType.ARCHON)
                continue;
            enemyAdvanceStrength += 1000 / (10 + rc.senseRubble(r.location));
        }
        //determine what our advance would be
        {
            MapLocation from = rc.getLocation();
            Direction d = from.directionTo(nearest);
            MapLocation option1 = from.add(d);
            MapLocation option2 = from.add(d.rotateLeft());
            MapLocation option3 = from.add(d.rotateRight());
            int rubble0 = rc.senseRubble(from) * 2 + 10;
            int rubble1 = rc.canSenseLocation(option1) ? rc.senseRubble(option1) : 100;
            int rubble2 = rc.canSenseLocation(option2) ? rc.senseRubble(option2) : 100;
            int rubble3 = rc.canSenseLocation(option3) ? rc.senseRubble(option3) : 100;
            if (rubble1 <= rubble2 && rubble1 <= rubble3 && rubble1 <= rubble0 && rc.canMove(d))
                toMove = d; //rc.move(d);
            else if (rubble2 <= rubble3 && rubble2 <= rubble0 && rc.canMove(d.rotateLeft()))
                toMove = d.rotateLeft(); //rc.move(d.rotateLeft());
            else if (rubble3 <= rubble0 && rc.canMove(d.rotateRight()))
                toMove = d.rotateRight(); //rc.move(d.rotateRight());
            //return true;
        }
        MapLocation home = intToLoc(rc.readSharedArray(INDEX_ARCHON_LOC));
        double spaceFactor = 10 * home.distanceSquaredTo(myLoc) /
                (double)(max(rc.getMapWidth() - home.x - 1, home.x) *
                        max(rc.getMapHeight() - home.y - 1, home.y));
        if (!(toMove != null && enemyAdvanceStrength * spaceFactor < myAdvanceStrength + 1000 / (10 + rc.senseRubble(rc.getLocation().add(toMove))) && rc.isActionReady() && rc.getMovementCooldownTurns() < 8)) {
            toMove = null; //don't advance if condition isn't met.
        }
        int myHoldStrength = 0;
        for (RobotInfo r : friends) {
            if (r.type != SOLDIER)
                continue;
            int infDist = max(Math.abs(r.location.x - rc.getLocation().x), Math.abs(r.location.y - rc.getLocation().y));
            if (infDist <= nearestInfDistance)
                myHoldStrength += 1000 / (10 + rc.senseRubble(r.location));
        }
        int enemyHoldStrength = 0;
        for (RobotInfo r : enemies) {
            if (r.type == RobotType.MINER || r.type == RobotType.BUILDER || r.type == RobotType.ARCHON)
                continue;
            if (max(Math.abs(r.location.x - rc.getLocation().x), Math.abs(r.location.y - rc.getLocation().y)) < 4)
                enemyHoldStrength += 1000 / (10 + rc.senseRubble(r.location));
        }
        if (toMove == null && (myHoldStrength < enemyHoldStrength || (!rc.isActionReady() && enemyHoldStrength > 0))) {
            //retreat
            //always look for low rubble retreats
            int myx = rc.getLocation().x;
            int myy = rc.getLocation().y;
            double best = 10 + rc.senseRubble(rc.getLocation());
            Direction bestD = null;
            for (Direction d : Direction.allDirections()) {
                MapLocation l = rc.getLocation().add(d);
                if (!rc.onTheMap(l)) continue;
                double r = 10 + rc.senseRubble(l);
                if ((l.x - myx) * (nearest.x - myx) < 0)
                    r /= 1.5;
                if ((l.y - myy) * (nearest.y - myy) < 0)
                    r /= 1.5;
                if (r < best && rc.canMove(d)) {
                    best = r;
                    bestD = d;
                }
            }
            if (bestD != null)
                rc.move(bestD);
            //moveInDirection(nearest.directionTo(rc.getLocation()));
            //return true;
        } else if (toMove != null)
            rc.move(toMove);
        else {
            //holding; priority is the high ground.
            int minRubble = rc.senseRubble(rc.getLocation());
            Direction minRubbleDir = Direction.CENTER;
            for (Direction d : directions) {
                int rubble = rc.senseRubble(rc.getLocation().add(d));
                if (rubble < minRubble && rc.canMove(d)) {
                    minRubble = rubble;
                    minRubbleDir = d;
                }
            }
            if (minRubbleDir != Direction.CENTER) {
                rc.move(minRubbleDir);
            }

        }
        rc.setIndicatorString("adv " + myAdvanceStrength + " oppAdv " + enemyAdvanceStrength + " hold " + myHoldStrength + " oppHold " + enemyHoldStrength);
        return true;
    }

    private void bytecodeTest() {
        int[][] a = new int[10][10];
        int c55 = 0;
        int c77;
        int b = Clock.getBytecodeNum();
        a[5][5] = 5;
        int b1 = Clock.getBytecodeNum();
        c55 = rc.getID() % 10;
        int b2 = Clock.getBytecodeNum();
        a[INDEX_ENEMY_HQ][INDEX_ENEMY_HQ] = 6;
        int b3 = Clock.getBytecodeNum();
        c55 = a[5][5];
        c77 = c55;
        int b4 = Clock.getBytecodeNum();

        rc.setIndicatorString("[][] = " + (b1 - b) + " normal " + (b2 - b1) + " " + (b3 - b2) + " read " + (b4 - b3) + " " + c77);
    }

    boolean healing = false;
    boolean dying = false, there = false;

    private void movement() throws GameActionException {
        if(dying) {
            if(!there) {
                MapLocation home = intToLoc(rc.readSharedArray(INDEX_ARCHON_LOC));
                moveToward(home);

                if (home.distanceSquaredTo(myLoc) <= ARCHON.visionRadiusSquared) {
                    there = true;
                    movementTarget = null;
                }
            }

            if(there) {
                MapLocation[] nonlead =
                        rc.getAllLocationsWithinRadiusSquared(myLoc,
                                SOLDIER.visionRadiusSquared);

                int best = movementTarget == null ? 10000 :
                        myLoc.distanceSquaredTo(movementTarget);

                for(MapLocation loc : nonlead) {
                    if(rc.senseLead(loc) > 0) continue;

                    int dist = myLoc.distanceSquaredTo(loc);

                    if(dist < best) {
                        movementTarget = loc;
                        best = dist;
                    }
                }

                if(movementTarget != null) {
                    moveToward(movementTarget);
                } else {
                    rc.disintegrate();
                }

                if(rc.senseLead(myLoc) == 0) {
                    rc.disintegrate();
                }
            }

            return;
        }

        if(rc.getHealth() < 10) {
            if(!healing) {
                int healees = rc.readSharedArray(INDEX_HEALING);
                if (healees < MAX_HEALS) {
                    healing = true;
                    rc.writeSharedArray(INDEX_HEALING, healees + 1);
                } else {
                    dying = true;
                }
            }
        }

        if(healing) {
            if(rc.getHealth() >= 49) {
                healing = false;
                int healees = rc.readSharedArray(INDEX_HEALING);
                rc.writeSharedArray(INDEX_HEALING, healees - 1);
            } else {
                MapLocation home = intToLoc(rc.readSharedArray(INDEX_ARCHON_LOC));
                if (home.distanceSquaredTo(myLoc) >
                        ARCHON.actionRadiusSquared)
                    moveToward(home);

                return;
            }
        }

        if (micro())
            return;

        if (movementTarget != null && rc.canSenseLocation(movementTarget))
            movementTarget = null;
        MapLocation x = super.getNearestEnemyChunk();
        if (x != null) movementTarget = x;
        if (movementTarget == null)
            movementTarget = super.getRandomKnownEnemyHQ();
        if (movementTarget == null)
            movementTarget = super.getRandomPossibleEnemyHQ();
        moveToward(movementTarget);
    }

    public void attack() throws GameActionException {
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length == 0) return;
        RobotInfo bestTarget = enemies[0];
        for (RobotInfo rb : enemies) {
            if ((!(bestTarget.type == SOLDIER || bestTarget.type == WATCHTOWER) &&
                    (rb.type == SOLDIER || rb.type == WATCHTOWER))
                    || ((!(bestTarget.type == SOLDIER || bestTarget.type == WATCHTOWER) ||
                    (rb.type == SOLDIER || rb.type == WATCHTOWER)) &&
                    bestTarget.health > rb.health)) {
                bestTarget = rb;
            }
        }
        if (rc.canAttack(bestTarget.location))
            rc.attack(bestTarget.location);
    }
}
