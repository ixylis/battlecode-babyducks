package matir;

import battlecode.common.*;

import java.util.ArrayList;

import static battlecode.common.RobotType.*;
import static java.lang.Math.*;

public strictfp class Archon extends Building {

    final double VOLATILITY = 4;
    double effPrevLead = 200, expenditure = 0, effPrevIncomePerMiner = 2;
    double minerCount = 0.1, soldierCount = 5, budgetOverflow = 0;

    public Archon(RobotController rc) throws GameActionException {
        super(rc);
        // TODO: Analyze Map
        // TODO: Analyze Anomaly Schedule
    }

    @Override
    void step() throws GameActionException {

        double curLead = rc.getTeamLeadAmount(US);
        double effCurLead = curLead / rc.getArchonCount();
        double effIncome = effCurLead - effPrevLead + expenditure;
        double effIncomePerMiner = effIncome / minerCount;
        double minerWeight = soldierCount * effPrevIncomePerMiner;
        double soldierWeight = minerCount * effIncomePerMiner;
        double totalWeight = minerWeight + soldierWeight;
        double normMinerWeight = minerWeight/totalWeight;

        effPrevLead = effCurLead;
        effPrevIncomePerMiner = effIncomePerMiner;

        double budget = effCurLead - budgetOverflow;
        double maxMoney = min(budget * (1 + VOLATILITY / rc.getArchonCount()), curLead);

        expenditure = 0;
        budgetOverflow = max(-budget, 0);

        RobotType buildType;

        RobotInfo[] enemies = rc.senseNearbyRobots(ARCHON.visionRadiusSquared, THEM);
        if(enemies.length > 0) {
            buildType = SOLDIER;
        } else {
            buildType = rng.nextDouble() < normMinerWeight ? MINER : SOLDIER;
        }

        if(buildType.buildCostLead > maxMoney)
        {
            return;
        }

        if(build(buildType)) {
            if(buildType == MINER) minerCount++;
            if(buildType == SOLDIER) soldierCount++;

            expenditure = buildType.buildCostLead;
            budgetOverflow = max(expenditure - budget, 0);
        }
    }

    boolean build(RobotType buildType) throws GameActionException {
        ArrayList<Direction> dirs = new ArrayList<Direction>();

        for(Direction d : directions) {
            if(rc.canBuildRobot(buildType, d)) {
                dirs.add(d);
            }
        }

        if(dirs.isEmpty()) return false;

        rc.buildRobot(buildType, randDirByWeight(dirs));
        return true;
    }
}
