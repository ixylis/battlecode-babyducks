package anthony;

import battlecode.common.*;

import static battlecode.common.RobotType.*;

public class Sage extends Robot {
    Sage(RobotController r) throws GameActionException {
        super(r);
    }

    private MapLocation movementTarget = null;

    public void turn() throws GameActionException {
        if (rc.isMovementReady())
            movement();
        if (rc.isActionReady())
            attack();
        else
            super.updateEnemySoliderLocations();
        if (rc.isMovementReady()) movement();
        super.updateEnemyHQs();
        //rc.setIndicatorDot(Robot.intToLoc(rc.readSharedArray(INDEX_ENEMY_HQ+rc.getRoundNum()%4)), 190, 0, 190);
        rc.setIndicatorDot(intToChunk(rc.readSharedArray(INDEX_ENEMY_LOCATION + rc.getRoundNum() % NUM_ENEMY_SOLDIER_CHUNKS)), 1, 255, 1);

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
        int nearestInfDistance = Math.max(Math.abs(nearest.x - rc.getLocation().x), Math.abs(nearest.y - rc.getLocation().y));

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
            if (Math.max(Math.abs(to.x - rc.getLocation().x), Math.abs(to.y - rc.getLocation().y)) <= 3)
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
            int rubble0 = rc.senseRubble(from);
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
        if (!(toMove != null && enemyAdvanceStrength * 2 < myAdvanceStrength + 1000 / (10 + rc.senseRubble(rc.getLocation().add(toMove))) && rc.isActionReady() && rc.getMovementCooldownTurns() < 8)) {
            toMove = null; //don't advance if condition isn't met.
        }
        int myHoldStrength = 0;
        for (RobotInfo r : friends) {
            if (r.type != SOLDIER)
                continue;
            int infDist = Math.max(Math.abs(r.location.x - rc.getLocation().x), Math.abs(r.location.y - rc.getLocation().y));
            if (infDist <= nearestInfDistance)
                myHoldStrength += 1000 / (10 + rc.senseRubble(r.location));
        }
        int enemyHoldStrength = 0;
        for (RobotInfo r : enemies) {
            if (r.type == RobotType.MINER || r.type == RobotType.BUILDER || r.type == RobotType.ARCHON)
                continue;
            if (Math.max(Math.abs(r.location.x - rc.getLocation().x), Math.abs(r.location.y - rc.getLocation().y)) < 4)
                enemyHoldStrength += 1000 / (10 + rc.senseRubble(r.location));
        }
        if (toMove == null && (myHoldStrength < enemyHoldStrength * 1.2 || (!rc.isActionReady() && enemyHoldStrength > 0))) {
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

    private void movement() throws GameActionException {
        /* if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(SAGE.visionRadiusSquared,
                    rc.getTeam().opponent());

            boolean[] enemyAfterMove = new boolean[8];
            int moveable = 0;

            for (Direction dir : Direction.cardinalDirections()) {
                for (RobotInfo enemy : enemies) {
                    if (enemy.location.distanceSquaredTo(
                            myLoc.add(dir)) < SAGE.actionRadiusSquared) {
                        enemyAfterMove[dir.ordinal()] = true;
                        moveable++;
                        break;
                    }
                }
            }
            if(moveable > 0) {
                Direction[] valid = new Direction[moveable];
                int i = 0;
                for(int j = 0; j < moveable; j++) {
                    while(!enemyAfterMove[i]) i++;
                    valid[j] = Direction.cardinalDirections()[i];
                }

                moveInDirection(randDirByWeight(valid, rubbleWeight));
                return;
            }
        } */

        if (micro()) return;

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
        int radius = SAGE.actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length == 0) return;
        RobotInfo bestTarget = enemies[0];
        for (RobotInfo rb : enemies) {
            if (bestTarget.health < SAGE.damage) {
                if (rb.health < SAGE.damage) {
                    if (rb.health > bestTarget.health) {
                        bestTarget = rb;
                    }
                }
            } else {
                if (rb.health < SAGE.damage) {
                    bestTarget = rb;
                } else {
                    if (rb.health < bestTarget.health) {
                        if (isAttacker(bestTarget)) {
                            if (isAttacker(rb)) {
                                if (rb.health < bestTarget.health)
                                    bestTarget = rb;
                            }
                        } else {
                            if (isAttacker(rb)) {
                                bestTarget = rb;
                            } else {
                                if (rb.health < bestTarget.health)
                                    bestTarget = rb;
                            }
                        }
                    }
                }
            }
        }
        if (rc.canAttack(bestTarget.location))
            rc.attack(bestTarget.location);
    }
}



