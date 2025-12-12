package bot.wuliang.service

import bot.wuliang.entity.WfMissionEntity

interface WfMissionService {
    fun createMission(mission: WfMissionEntity)

    fun createMissionByList(mission: List<WfMissionEntity>)

    fun getAllMission(): List<WfMissionEntity>
    fun getAllMissionByQuery(): List<WfMissionEntity>


    fun upsertMissions(missions: List<WfMissionEntity>)
    fun getMissionByItem(itemName: String): MutableList<WfMissionEntity>
}