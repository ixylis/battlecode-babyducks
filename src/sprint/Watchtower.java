package sprint;

import battlecode.common.*;

import static java.lang.Math.*;

public class Watchtower extends Robot {
    int lastActive;
    MapLocation movementTarget, highground = new MapLocation(0, 0);
    double highgroundRubble = 10000;

    Watchtower(RobotController r) throws GameActionException {
        super(r);
        lastActive = rc.getRoundNum();
    }

    public void turn() throws GameActionException {
        if (rc.getMode() == RobotMode.PROTOTYPE) return;

        int radius = rc.getType().visionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);

        MapLocation[] allLocs = rc.getAllLocationsWithinRadiusSquared(myLoc, 8);
        for (MapLocation l : allLocs) {
            if (rc.senseRubble(l) * sqrt(myLoc.distanceSquaredTo(l)) <
                    highgroundRubble * sqrt(myLoc.distanceSquaredTo(highground))) {
                highgroundRubble = rc.senseRubble(l);
                highground = l;
            }
        }

        rc.setIndicatorDot(highground,0,0,255);

        if (rc.getMode() == RobotMode.PORTABLE) {
            if ((enemies.length > 0) || (movementTarget != null &&
                    myLoc.isWithinDistanceSquared(movementTarget, 50))) {
                if ((highground == myLoc || rc.canSenseRobotAtLocation(highground))
                        && rc.canTransform()) {
                    rc.transform();
                } else {
                    moveToward(highground);
                }
                lastActive = rc.getRoundNum();
            } else {
                moveToward(movementTarget);
            }
        } else {
            if (enemies.length > 0) {
                RobotInfo toAttack = enemies[0];
                for (RobotInfo enemy : enemies) {
                    if (enemy.health < toAttack.health) {
                        toAttack = enemy;
                    }
                }

                lastActive = rc.getRoundNum();
                if (rc.canAttack(toAttack.location))
                    rc.attack(toAttack.location);
            } else {
                if (lastActive < rc.getRoundNum() - 20) {

                    movementTarget = super.getNearestEnemyChunk();
                    if (movementTarget == null)
                        movementTarget = super.getRandomKnownEnemyHQ();
                    if (movementTarget == null)
                        movementTarget = super.getRandomPossibleEnemyHQ();
                    if (movementTarget == null)
                        movementTarget = corners[rng.nextInt(4)];

                    if(!myLoc.isWithinDistanceSquared(movementTarget, 50) && rc.canTransform())
                        rc.transform();

                    double theta = (rng.nextInt(2)-0.5) * (rng.nextDouble() + 1) * PI/2;
                    int dx = movementTarget.x - myLoc.x, dy = movementTarget.y - myLoc.y;
                    movementTarget = myLoc.translate((int) (dx * cos(theta) - dy * sin(theta)),
                            (int) (dy * cos(theta) + dx * sin(theta)));

                }
            }
        }
    }
}
