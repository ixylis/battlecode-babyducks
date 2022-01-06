package matirdump;

import battlecode.common.*;

import static battlecode.common.RobotType.*;
import static java.lang.Math.*;

import java.util.*;

public strictfp class Archon extends Building {

    final static double MINER_TO_SOLDIER_MIN = 0.33;
    final static double MINER_TO_SOLDIER_MAX = 0.67;
    final static double MINER_CONST = 0.2;
    final static double POTENTIAL_WEIGHT = 0.25;
    final static double CORNER_WEIGHT = 0.25;
    final static double ARCHON_WEIGHT = 0.1;
    final static double ENEMY_WEIGHT = 0.01;
    
    int prevLead, minerCount, soldierCount;
    MapLocation lastLoc;

    public Archon(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    void init() throws GameActionException {
        int order = (rc.readSharedArray(11) & 0xF000) >> 12;
        writeHext(order, myloc.x);
        writeHext(order + 1, myloc.y);
        rc.writeSharedArray(11, order + 1);

        // TODO: Analyze map
        // TODO: Analyze anomaly schedule
    }

    @Override
    void step() throws GameActionException {
        rc.setIndicatorDot(myloc, 255,0,0);
        int lead = rc.getTeamLeadAmount(US);
        int income = lead - prevLead;
        prevLead = lead;

        int archons = rc.getArchonCount();
        double allowedLead = lead/(double)archons;

        RobotType buildType;
        MapLocation buildTarget;
        RobotInfo[] erbinfo = rc.senseNearbyRobots(ARCHON.visionRadiusSquared, THEM);

        if(erbinfo.length > 0) {
            // Defensive Soldier
            buildType = SOLDIER;
            int ri = rng.nextInt(erbinfo.length);
            buildTarget = erbinfo[ri].getLocation();
        } else if(soldierCount * MINER_TO_SOLDIER_MIN > minerCount) {
            // Miner on Soldier 
            buildTarget = lastLoc;
            buildType = MINER;
        } else if(soldierCount * MINER_TO_SOLDIER_MAX < minerCount) {
            // Soldier on Miner
            buildTarget = lastLoc;
            buildType = SOLDIER;
        } else {
            // Relative priority computation
            double minerPriority = 0, soldierPriority;

            if(resources.isEmpty()) {
                minerPriority = 0;
            } else {
                for(Resource resource : resources) {
                    minerPriority += MINER_CONST * resource.value();
                }
            }
            
            soldierPriority = potentials.size() * POTENTIAL_WEIGHT +
                              corners.size() * CORNER_WEIGHT +
                              enemyArchons.size() * ARCHON_WEIGHT +
                              enemies.size() * ENEMY_WEIGHT;
            
            if(minerPriority * soldierCount > soldierPriority * minerCount) {
                // make miner
                buildType = MINER;
                Resource bres = new Resource(myloc, 0);

                for(Resource resource : resources) {
                    if(resource.value() > bres.value()) {
                        bres = resource;
                    }
                }

                buildTarget = bres.loc;
                resources.remove(bres);

            } else {
                // make soldier
                buildType = SOLDIER;
                if(potentials.isEmpty()) {
                    if(corners.isEmpty()) {
                        if(enemyArchons.isEmpty()) {
                            if(enemies.isEmpty()) {
                                buildTarget = myloc;
                            } else {
                                buildTarget = Collections.min(enemies, distComp);
                            }
                        } else {
                            buildTarget = Collections.min(enemyArchons, distComp);
                        }
                    } else {
                        buildTarget = Collections.max(corners, distComp);
                    }
                } else {
                    buildTarget = Collections.min(potentials, distComp);
                }
            }
        }

        if(build(buildType, buildTarget)) {
            if(buildType == MINER) minerCount++;
            else if(buildType == SOLDIER) soldierCount++;
            lastLoc = buildTarget;
        } else {
            // can't build competently, try to repair
            RobotInfo[] frbinfo = rc.senseNearbyRobots(ARCHON.actionRadiusSquared, US);
            RobotInfo brb = new RobotInfo(0, US, RobotType.MINER, RobotMode.DROID,1,0, myloc);

            for(RobotInfo rb : frbinfo) {
                if(rc.canRepair(rb.getLocation()) && rb.getHealth() > brb.getHealth()) {
                    brb = rb;
                }
            }

            if(rc.canRepair(brb.getLocation())) rc.repair(brb.getLocation());
        }
    }

    boolean build(RobotType buildType, MapLocation buildTarget) throws GameActionException {

        int budget = rc.getTeamLeadAmount(US) / rc.getArchonCount();
        if(buildType.buildCostLead > budget) return false;

        ArrayList<Direction> validDirs = new ArrayList<Direction>();
        
        for(Direction dir : directions) {
            if(rc.canBuildRobot(buildType, dir)) {
                validDirs.add(dir);
            }
        }

        if(validDirs.size() == 0) return false;

        Direction dir;
        
        if(myloc == buildTarget) {
            dir = validDirs.get(rng.nextInt(validDirs.size()));
        } else {
            double theta = atan2(buildTarget.y - myloc.y, buildTarget.x - myloc.x);
            dir = Collections.min(validDirs, new DirComp(theta));
        }

        rc.buildRobot(buildType, dir);
        rc.writeSharedArray(11, (buildTarget.y << 6) | buildTarget.x);
        return true;
    }
}
