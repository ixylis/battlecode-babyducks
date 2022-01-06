package matirdump;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Soldier extends Droid {

    MapLocation permaLoc;

    public Soldier(RobotController rc) throws GameActionException {
        super(rc);
    }

//    @Override
//    void init() throws GameActionException {
//        super.init();
//    }

    @Override
    void step() throws GameActionException {
        updateInfo();

        if(targetLoc == null) this.getTargetLocation();
        move();

        RobotInfo[] erbinfo =
               rc.senseNearbyRobots(RobotType.SOLDIER.actionRadiusSquared, THEM);

        MapLocation attackLoc = null;
        int minHealth = 0;

        if(erbinfo.length > 0) {
            for(RobotInfo rb : erbinfo)  {
                int health = rb.getHealth();
                if(health < minHealth) {
                    MapLocation loc = rb.getLocation();
                    if(rc.canAttack(loc)) {
                        minHealth = health;
                        attackLoc = loc;
                    }
                }
            }
        }

        if(rc.canAttack(attackLoc)) rc.attack(attackLoc);
    }

    void updateInfo() throws GameActionException {
        for(MapLocation loc : getLocsInRange(RobotType.SOLDIER.visionRadiusSquared)) {
            if(corners.contains(loc)) corners.remove(loc);
            if(potentials.contains(loc)) potentials.remove(loc);
            if(rc.canSenseRobotAtLocation(loc)) {
                if(rc.senseRobotAtLocation(loc).getType() == ARCHON) {
                    if(!enemyArchons.contains(loc)) {
                        enemyArchons.add(loc);
                        int ord = rc.readSharedArray(11);
                        rc.writeSharedArray(11, ord + 1);
                        writeHext(ord - rc.getArchonCount(), loc.y << 6 | loc.x);
                    }
                } else {
                    enemies.add(loc);
                }
            } else {
                if(enemies.contains(loc)) {
                    enemies.remove(loc);
                }
            }
        }
    }

    void getTargetLocation() {

    }

}
