package matirold;

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
        rc.setIndicatorDot(intToChunk(rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION + rc.getRoundNum() % NUM_ENEMY_UNIT_CHUNKS)), 1, 255, 1);
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
        outer:
        for(RobotInfo r : enemies) {
            for(int i=0;i<recentEnemies.length;i++) {
                if(recentEnemies[i]!= null && recentEnemies[i].ID == r.ID) {
                    recentEnemies[i] = r;
                    recentEnemiesRounds[i] = rc.getRoundNum();
                    continue outer;
                }
            }
            int oldest=0;
            for(int i=0;i<recentEnemies.length;i++) {
                if(recentEnemiesRounds[i] < recentEnemiesRounds[oldest])
                    oldest = i;
            }
            recentEnemies[oldest] = r;
            recentEnemiesRounds[oldest] = rc.getRoundNum();
        }
        boolean existsRecentEnemy = false;
        for(int i=0;i<recentEnemies.length;i++) {
            if(recentEnemiesRounds[i] > rc.getRoundNum() - 5) {
                existsRecentEnemy = true;
                //break;
            } else {
                recentEnemies[i] = null;
            }
        }
        if(!existsRecentEnemy) return false;
        int enemyStrength = 0;// + 30;
        int enemyHP = 0;// + 50;
        MapLocation nearest = null;
        for(int i=0;i<recentEnemies.length;i++) {
            //if(r.type == RobotType.MINER) continue;
            RobotInfo r = recentEnemies[i];
            if(recentEnemiesRounds[i] <= rc.getRoundNum() - 5) continue;
            if(r.type == rc.getType()) {
                enemyStrength += 3*100/(10+(rc.canSenseLocation(r.location)? rc.senseRubble(r.location) : 0));
                enemyHP += r.health;
            }
            if(nearest==null || rc.getLocation().distanceSquaredTo(nearest) > rc.getLocation().distanceSquaredTo(r.location))
                nearest = r.location;

        }
        int nearestInfDistance = Math.max(Math.abs(nearest.x - rc.getLocation().x), Math.abs(nearest.y - rc.getLocation().y));
        int friendlyStrength = 3*100/(10+rc.senseRubble(rc.getLocation()));
        int friendlyHP = rc.getHealth();
        boolean closerFriend = false;
        for(RobotInfo r : friends) {
            int dist = Math.max(Math.abs(nearest.x - r.location.x), Math.abs(nearest.y - r.location.y));
            if(nearestInfDistance >= dist) {
                if(nearestInfDistance > dist)
                    closerFriend = true;
                friendlyStrength += 3*100/(10+rc.senseRubble(r.location));
                friendlyHP += r.health;
                rc.setIndicatorDot(r.location, 0, 255, 0);
            }
        }
        int[] nearbyRubble = new int[9];
        for(int i=0;i<9;i++) {
            MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
            nearbyRubble[i] = rc.onTheMap(m)?rc.senseRubble(m):0;
        }
        boolean[] canShootFrom = new boolean[9];
        boolean[] canMove = new boolean[9];
        int[] enemiesInRange = new int[9];
        int bestDir=-1;
        //String s = "";
        for(int i=0;i<9;i++) {
            //if(rc.senseRobotAtLocation(rc.getLocation().add(Direction.allDirections()[i]))!=null) continue;
            //if(bestDir!=-1 && nearbyRubble[bestDir] < nearbyRubble[i]) continue;
            if(i<8 && !rc.canMove(Direction.allDirections()[i]))
                continue;
            else
                canMove[i] = true;
            for(RobotInfo r : enemies) {
                if(rc.getLocation().add(Direction.allDirections()[i]).distanceSquaredTo(r.location) <= RobotType.SOLDIER.actionRadiusSquared) {
                    if(r.type == RobotType.SOLDIER)
                        enemiesInRange[i]++;
                    canShootFrom[i] = true;
                }
            }
            if((bestDir==-1 || (enemiesInRange[i]+1) * (nearbyRubble[i] + 10) < (nearbyRubble[bestDir] + 10) * (1+enemiesInRange[bestDir])) && canShootFrom[i]) {
                bestDir = i;
            }
        }
        if(rc.getID() == 12792) {
            //rc.setIndicatorString(Arrays.toString(enemiesInRange)+s+rc.canMove(Direction.SOUTH));
        }
        //bestDir is the location with the least rubble*(enemies in range) which can shoot at least 1 enemy
        //if you can shoot from where you are standing, do that first (if it's rubble is low)
        if(canShootFrom[8] && nearbyRubble[8]==nearbyRubble[bestDir])
            attack();
        //if it is not possible to shoot this turn, what is the correct direction to advance in?
        //just move toward the enemy then
        if(bestDir==-1) {
            moveToward(nearest);
            return true;
            /*
            for(int i=0;i<9;i++) {
                if(Direction.allDirections()[i] == rc.getLocation().directionTo(nearest)) {
                    bestDir = i;
                    break;
                }
            }*/
        }
        Direction d = nearest.directionTo(rc.getLocation());
        int di=0;
        switch(d) {
            case NORTH: di=0; break;
            case NORTHEAST: di=1; break;
            case EAST: di=2; break;
            case SOUTHEAST: di=3; break;
            case SOUTH: di=4; break;
            case SOUTHWEST: di=5; break;
            case WEST: di=6; break;
            case NORTHWEST: di=7; break;
            case CENTER: di=8; break;
        }
        int retreat1 = rc.canMove(d)&&enemiesInRange[di]==0? rc.senseRubble(rc.getLocation().add(d)) : 1000;
        d=d.rotateLeft();
        switch(d) {
            case NORTH: di=0; break;
            case NORTHEAST: di=1; break;
            case EAST: di=2; break;
            case SOUTHEAST: di=3; break;
            case SOUTH: di=4; break;
            case SOUTHWEST: di=5; break;
            case WEST: di=6; break;
            case NORTHWEST: di=7; break;
            case CENTER: di=8; break;
        }
        int retreat2 = rc.canMove(d)&&enemiesInRange[di]==0? rc.senseRubble(rc.getLocation().add(d)) : 1000;
        d=d.rotateRight().rotateRight();
        switch(d) {
            case NORTH: di=0; break;
            case NORTHEAST: di=1; break;
            case EAST: di=2; break;
            case SOUTHEAST: di=3; break;
            case SOUTH: di=4; break;
            case SOUTHWEST: di=5; break;
            case WEST: di=6; break;
            case NORTHWEST: di=7; break;
            case CENTER: di=8; break;
        }
        int retreat3 = rc.canMove(d)&&enemiesInRange[di]==0? rc.senseRubble(rc.getLocation().add(d)) : 1000;
        boolean shouldRetreat = false;
        //should we step foward and shoot?
        //if your strength beats the enemy by a factor of the rubble difference
        //if you don't advance, then consider retreating, but only if the rubble is sufficiently in your favor

        boolean shouldAdvance = false;
        int b = 999999;
        int bestMoveI=-1;
        //first determine if you should retreat
        //if you've already shot this turn, then simply move to the least rubble*enemies spot you can find.
        if(!rc.isActionReady() && enemiesInRange[8]>0) {
            //half assed retreat
            int best = (enemiesInRange[8]+1)*(nearbyRubble[8]+10);
            int bestI = 8;
            for(int i=0;i<8;i++) {
                int x=(enemiesInRange[i]+1)*(nearbyRubble[i]+10);
                if(x < best && canMove[i]) {
                    best = x;
                    bestI = i;
                }
            }
            if(bestI<8)
                rc.move(Direction.allDirections()[bestI]);
            bestMoveI=bestI*10;
        } else {
            if(enemyStrength*enemyHP * (10+nearbyRubble[bestDir]) *(10+nearbyRubble[bestDir]) * 3/2< friendlyStrength*friendlyHP*(10+nearbyRubble[8])*(10+nearbyRubble[8])) {
                shouldRetreat = false;
                shouldAdvance = true;
            } else if(closerFriend) {
                shouldRetreat = false;
                shouldAdvance = false;
            } else
                shouldRetreat = true;
            d = d.rotateLeft();
            Direction bestRetreatDir = d;
            if(shouldRetreat) {
                b = retreat1;
                if(retreat2 < b) {b = retreat2; bestRetreatDir = d.rotateLeft();}
                if(retreat3 < b) {b = retreat3; bestRetreatDir = d.rotateRight();}
                if(b <= nearbyRubble[8] || (b+10) * (b+10) * friendlyStrength*friendlyHP < (nearbyRubble[8]+10) * (nearbyRubble[8]+10) * enemyStrength*enemyHP) {
                    if(rc.isActionReady())
                        attack();
                    rc.move(bestRetreatDir);
                } else if((nearbyRubble[8]+10)*16/10 >= (nearbyRubble[bestDir]+10) && rc.isActionReady())
                    //if you haven't shot this turn, and retreating wasn't good enough to warrant, then walk forward and shoot anyway.
                    //you get about 1.6x more shots off by walking forward to shoot rather than sitting and waiting for the enmey to come.
                    shouldAdvance = true;
            }
            if(shouldAdvance && rc.isMovementReady()) {
                rc.move(Direction.allDirections()[bestDir]);
                if(rc.isActionReady())
                    attack();
            }
            if(!shouldAdvance && rc.isMovementReady()) {
                //rc.setIndicatorDot(rc.getLocation(), 255, 255, 255);
                //if you should neither advance nor retreat, move to the lowest rubble*enemies
                int best = (enemiesInRange[8]+1)*(nearbyRubble[8]+10);
                int bestI = 8;
                for(int i=0;i<8;i++) {
                    int x=(enemiesInRange[i]+1)*(nearbyRubble[i]+10);
                    if(x < best && canMove[i]) {
                        best = x;
                        bestI = i;
                    }
                }
                if(bestI<8)
                    rc.move(Direction.allDirections()[bestI]);
                bestMoveI=bestI;

            }
        }
        /*
        if(!closerFriend && (enemyStrength*enemyHP * (nearbyRubble[bestDir]+10) *(nearbyRubble[bestDir]+10) > 2 * friendlyStrength*friendlyHP * (nearbyRubble[8]+10) * (nearbyRubble[8]+10)
                || (!rc.isActionReady() && 2*enemyStrength*enemyHP > friendlyStrength*friendlyHP))) {
            int b = retreat1;
            retreatAttempted = true;
            Direction bestRetreatDir = d;
            if(retreat2 < b) {b = retreat2; bestRetreatDir = d.rotateLeft();}
            if(retreat3 < b) {b = retreat3; bestRetreatDir = d.rotateRight();}
            if((b+10) * (b+10) * friendlyStrength*friendlyHP < (nearbyRubble[8]+10) * (nearbyRubble[8]+10) * enemyStrength*enemyHP)
                rc.move(bestRetreatDir);
        }
        if((!retreatAttempted || (nearbyRubble[bestDir] < nearbyRubble[8])) && rc.canMove(Direction.allDirections()[bestDir]))
            rc.move(Direction.allDirections()[bestDir]);
        if(rc.isActionReady())
            attack();
            */
        rc.setIndicatorString("eHP "+enemyHP+" eS "+enemyStrength+" fHP "+friendlyHP+" fS "+friendlyStrength+
                " "+Direction.allDirections()[bestDir]+" "+shouldAdvance+" "+bestMoveI+shouldRetreat);
        rc.setIndicatorDot(nearest, 255, 0, 0);
        return true;
    }
    private void oldMicro() {

        /*
         * everyone moves forward one square toward the nearest enemy to my location.
         * for this purpose, we go one robot at a time, find the lowest rubble spot it can move to in that direction +/- 45 deg
         * declare that spot 'occupied by friendly forces'
         */
        /*
        Direction toMove=null;
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
        */
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
                    return;
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
