package sprint;

import battlecode.common.*;

import static battlecode.common.AnomalyType.CHARGE;
import static battlecode.common.AnomalyType.FURY;
import static battlecode.common.RobotType.*;
import static java.lang.Math.PI;
import static java.lang.Math.max;

public class Sage extends Robot {
    Sage(RobotController r) throws GameActionException {
        super(r);
    }

    private MapLocation movementTarget = null;

    public void turn() throws GameActionException {
        boolean m = micro();
        if(rc.isMovementReady() || rc.isActionReady()) {
            if(!m && rc.isMovementReady())
                movement();
        }
        super.updateEnemyLocations();
        //if (rc.isMovementReady()) movement();
        super.updateEnemyHQs();
        //rc.setIndicatorDot(Robot.intToLoc(rc.readSharedArray(INDEX_ENEMY_HQ+rc.getRoundNum()%4)), 190, 0, 190);
        rc.setIndicatorDot(intToChunk(rc.readSharedArray(INDEX_ENEMY_UNIT_LOCATION +
                rc.getRoundNum() % NUM_ENEMY_UNIT_CHUNKS)), 1, 255, 1);
        if(damageDealt > 100)
            rc.setIndicatorDot(rc.getLocation(), 0, 255, 255);
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
    int damageDealt = 0;
    int lastShotTurn = 0;
    private boolean micro() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        if(enemies.length==0) return false;
        boolean[] canMove = new boolean[9];
        //int[] enemiesInRange = new int[9];
        int[] dmg = new int[9];
        int enemyStrength = 0;
        int[] enemiesFarther = new int[9]; //enemies within one tile of range
        int bestRetreat=-1;
        int bestAdvance=-1;
        int[] nearbyRubble = new int[9];
        for(int i=0;i<9;i++) {
            MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
            nearbyRubble[i] = rc.onTheMap(m)?rc.senseRubble(m):1000;
        }
        for(RobotInfo r : enemies) {
            switch(r.type) {
            case SAGE: enemyStrength += 2; break;
            case SOLDIER: enemyStrength += 1; break;
            case WATCHTOWER: enemyStrength += 2 * r.level; break;
            default: break;
            }
        }
        for(int i=0;i<9;i++) {
            if(i<8 && !rc.canMove(Direction.allDirections()[i])) 
                continue;
            else
                canMove[i] = true;
            for(RobotInfo r : enemies) {
                MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
                if(m.isWithinDistanceSquared(r.location, RobotType.SOLDIER.actionRadiusSquared)) {
                    if(r.type == RobotType.SOLDIER || r.type == RobotType.SAGE) {
                        enemiesFarther[i]+=10;
                    }
                } else if(m.isWithinDistanceSquared(r.location, RobotType.SAGE.actionRadiusSquared)) {
                    switch(r.type) {
                    case SAGE:
                        enemiesFarther[i]+=2;
                        dmg[i]+=Math.min(r.health, 22);
                        break;
                    case SOLDIER:
                        enemiesFarther[i]+=1;
                        dmg[i]+=Math.min(r.health, 11);
                        break;
                    case MINER:
                        dmg[i]+=Math.min(r.health, 8);
                        break;
                    default: break;
                    }
                }
            }
            if((bestRetreat==-1 || (enemiesFarther[i]+1) * (nearbyRubble[i] + 10) < (nearbyRubble[bestRetreat] + 10) * (1+enemiesFarther[bestRetreat]))) {
                bestRetreat = i;
            }
            if((bestAdvance==-1 || (dmg[i]+100) + (nearbyRubble[i] + 10) < (nearbyRubble[bestAdvance] + 10) + (100+dmg[bestAdvance]))) {
                bestAdvance = i;
            }
        }
        //if we aren't shooting any time soon, retreat
        if(rc.getActionCooldownTurns() > 30 || (rc.getRoundNum()%30<0 && enemyStrength>1/*lastShotTurn > rc.getRoundNum()+30*/)) {
            if(bestRetreat!=8 && bestRetreat != -1) //don't bother if the optimal move is to stay where you are
                rc.move(Direction.allDirections()[bestRetreat]);
        } else {
            //otherwise, advance to shoot
            if(dmg[bestAdvance] < 45) {
                bestAdvance = -1;
                for(int i=0;i<9;i++) {
                    if((bestAdvance==-1 || nearbyRubble[i] < nearbyRubble[bestAdvance]) && dmg[i] > 0 && nearbyRubble[i] <= nearbyRubble[8]) {
                        bestAdvance = i;
                    }
                }
            }
            if(bestAdvance != 8 && bestAdvance != -1)
                rc.move(Direction.allDirections()[bestAdvance]);
        }
        RobotInfo[] enemies2 = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        int maxDmg=0;
        int dmgNow=0;
        for(RobotInfo r : enemies2) {
                switch(r.type) {
                case SAGE:
                    maxDmg+=Math.min(r.health, 22);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 22);
                    break;
                case SOLDIER:
                    maxDmg+=Math.min(r.health, 11);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 11);
                    break;
                case BUILDER:
                    maxDmg+=Math.min(r.health, 6);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 6);
                    break;
                case MINER:
                    maxDmg+=Math.min(r.health, 8);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 8);
                    break;
                case ARCHON:
                    maxDmg+=Math.min(r.health, 60);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 60);
                    break;
                case LABORATORY:
                    maxDmg+=Math.min(r.health, 10);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 10);
                    break;
                case WATCHTOWER:
                    maxDmg+=Math.min(r.health, 15);
                    if(r.location.isWithinDistanceSquared(rc.getLocation(), rc.getType().actionRadiusSquared))
                        dmgNow+=Math.min(r.health, 15);
                    break;
                default: break;
                }
        }
        for(int i=0;i<9;i++) {
            MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
            nearbyRubble[i] = rc.onTheMap(m)?rc.senseRubble(m):1000;
        }
        int minRubble = 1000;
        for(int i=0;i<9;i++) {
            if(minRubble > nearbyRubble[i])
                minRubble = nearbyRubble[i];
        }
        rc.setIndicatorString(maxDmg+" m "+rc.getMovementCooldownTurns()+" a "+rc.getActionCooldownTurns()+" minr "+minRubble+" totalD "+damageDealt);
        if(maxDmg < 2*dmgNow || rc.getHealth() < (20+rc.getMovementCooldownTurns())*maxDmg/100) {

            if(rc.isActionReady() && ((minRubble+10)*10>(nearbyRubble[8]+10)*7)  || rc.getHealth() < (20+rc.getMovementCooldownTurns())*maxDmg/100) {
                attack();
                if(!rc.isActionReady())
                    lastShotTurn = rc.getRoundNum();
            }
        }
        return true;
    }
    private boolean oldmicro() throws GameActionException {
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
                if(!rc.onTheMap(myLoc.add(d))) continue;
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
    MapLocation home;
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

        int friends = 0;
        for(RobotInfo r : rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam())) {
            if(r.type == RobotType.ARCHON) {
                home = r.location;
            }
            if(r.type == RobotType.SAGE || r.type == RobotType.SOLDIER)
                friends++;
        }

        if (movementTarget != null && rc.canSenseLocation(movementTarget))
            movementTarget = null;
        MapLocation x = super.getNearestEnemyChunk();
        MapLocation me = rc.getLocation();
        if(x != null) {
            if((rc.getActionCooldownTurns()>40 || rc.getRoundNum()%50<30) && 
                    (rc.getLocation().distanceSquaredTo(x) < 49 || (home == null || rc.getLocation().distanceSquaredTo(home) > rc.getLocation().distanceSquaredTo(x))) &&
                    friends < 10) {
                if(home==null) movementTarget = rc.getLocation().translate(me.x - x.x, me.y - x.y);
                else movementTarget = home;
            } else 
                movementTarget = x;
        }
        if (movementTarget == null)
            movementTarget = super.getRandomKnownEnemyHQ();
        if (movementTarget == null)
            movementTarget = super.getRandomPossibleEnemyHQ();
        moveToward(movementTarget);
        rc.setIndicatorLine(rc.getLocation(), movementTarget, 255, 255, 0);
    }

    public void attack() throws GameActionException {
        if (!rc.isActionReady()) return;
        int radius = SAGE.actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        RobotInfo[] friends = rc.senseNearbyRobots(radius, rc.getTeam());
        if (enemies.length == 0) return;
        int droidVal = 0, buildingVal = 0;
        int maxHP;
        RobotInfo bestTarget = enemies[0];

        for (RobotInfo rb : enemies) {
            maxHP = rb.type.getMaxHealth(rb.level);
            if (rb.type.isBuilding()) {
                if (rb.type == ARCHON) {
                    buildingVal += rb.health < (maxHP * 0.10) ?
                            rb.health + 100 : maxHP * 0.1;
                } else {
                    buildingVal += rb.health < (maxHP * 0.10) ?
                            rb.health + 15 : maxHP * 0.1;
                }
            } else {
                droidVal += rb.health < (maxHP * 0.22)
                        ? rb.health + 15 : maxHP * 0.22;
            }
            if (bestTarget.health <= SAGE.damage) {
                if (rb.health <= SAGE.damage) {
                    if (rb.health > bestTarget.health) {
                        bestTarget = rb;
                    }
                }
            } else {
                if (rb.health < SAGE.damage) {
                    bestTarget = rb;
                } else {
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

        for(RobotInfo r:friends) {
            switch (r.type) {
            case ARCHON: buildingVal -= 10000; break;
            case WATCHTOWER: buildingVal -= Math.min(33, r.health); break;
            case LABORATORY: buildingVal -= Math.min(22, r.health); break;
            default:
                break;
            }
        }

        int hitval = (bestTarget.health <= SAGE.damage) ?
                bestTarget.health + 15 : SAGE.damage;

        if(hitval >= max(droidVal, buildingVal)) {
            if (rc.canAttack(bestTarget.location))
                rc.attack(bestTarget.location);
            damageDealt += Math.min(bestTarget.health, 45);

            return;
        }

        if(droidVal >= buildingVal) {
            if(rc.canEnvision(CHARGE)) {
                rc.envision(CHARGE);
                for(RobotInfo r:enemies) {
                    switch (r.type) {
                    case SOLDIER: damageDealt += Math.min(11, r.health); break;
                    case MINER: damageDealt += Math.min(8, r.health); break;
                    case SAGE: damageDealt += Math.min(22, r.health); break;
                    case BUILDER: damageDealt += Math.min(8, r.health); break;
                    default:
                        break;
                    }
                }
                return;
            }
        }

        if(rc.canEnvision(FURY)) {
            rc.envision(FURY);
            for(RobotInfo r:enemies) {
                switch (r.type) {
                case ARCHON: damageDealt += Math.min(132, r.health); break;
                case WATCHTOWER: damageDealt += Math.min(33, r.health); break;
                case LABORATORY: damageDealt += Math.min(22, r.health); break;
                default:
                    break;
                }
            }
            for(RobotInfo r:friends) {
                switch (r.type) {
                case ARCHON: damageDealt -= Math.min(132, r.health); break;
                case WATCHTOWER: damageDealt -= Math.min(33, r.health); break;
                case LABORATORY: damageDealt -= Math.min(22, r.health); break;
                default:
                    break;
                }
            }
        }
    }
}



