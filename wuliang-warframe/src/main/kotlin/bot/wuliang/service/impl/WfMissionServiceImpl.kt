package bot.wuliang.service.impl

import bot.wuliang.entity.WfMissionEntity
import bot.wuliang.repository.WfMissionRepository
import bot.wuliang.service.WfMissionService
import org.bson.Document
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import javax.annotation.Resource


/**
 * @description: TODO
 * @author Nature Zero
 * @date 2024/11/24 23:40
 */
@Service
class WfMissionServiceImpl : WfMissionService {
    @Resource
    private lateinit var wfMissionRepository: WfMissionRepository

    @Resource
    private lateinit var mongoTemplate: MongoTemplate
    override fun createMission(mission: WfMissionEntity) {
        wfMissionRepository.save(mission)
    }

    override fun createMissionByList(mission: List<WfMissionEntity>) {
        wfMissionRepository.saveAll(mission)
    }

    override fun getAllMission(): List<WfMissionEntity> {
        return wfMissionRepository.findAll()
    }

    override fun getAllMissionByQuery(): List<WfMissionEntity> {
        val query = Query()
        // 添加查询条件，查询 mission_type_zh 包含 "测试" 的文档
        query.addCriteria(Criteria.where("mission_type_zh").regex("测试1", "i"))


        // 设置投影，选择返回的字段
//        query.fields().include("missionName","missionType")
        return mongoTemplate.find(query, WfMissionEntity::class.java)
    }

    override fun upsertMissions(missions: List<WfMissionEntity>) {
        val bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, WfMissionEntity::class.java)

        missions.forEach { mission ->
            // 创建查询条件，匹配 missionName 和 missionType
            val query = Query(
                Criteria.where("mission_name").`is`(mission.missionName)
                    .and("mission_type").`is`(mission.missionType)
            )

//            // 创建更新内容
//            val update = Update()
//                .set("rotation_data", mission.rotationData) // 替换为需要更新的字段

            // 创建更新内容
            val update = Update()
                .set("rotation_data", mission.rotationData) // 更新 rotation_data
                .set("mission_type_zh", mission.missionTypeZh) // 更新 missionTypeZh
                .set("mission_location", mission.missionLocation) // 更新地点
                .set("mission_name", mission.missionName) // 确保插入时 mission_name 被设置
                .set("mission_type", mission.missionType) // 确保插入时 mission_type 被设置


            // 添加 upsert 操作
            bulkOps.upsert(query, update)
        }

        // 执行批量操作
        bulkOps.execute()
    }


    override fun getMissionByItem(itemName: String):
            MutableList<WfMissionEntity> {
        // 使用 $objectToArray 将 rotation_data 转换为数组
        val addFieldsStage = Aggregation.addFields()
            .addFieldWithValue(
                "rotationArray",
                Document(
                    "\$objectToArray", "\$rotation_data"
                ) // 将对象转换为键值对数组
            )
            .build()

        // $reduce 阶段合并所有数组
        val reduceOperation = Document(
            "\$reduce", Document()
                .append("input", "\$rotationArray.v") // 提取键值对的 v 部分
                .append("initialValue", emptyList<Any>()) // 初始化为空数组
                .append(
                    "in", Document(
                        "\$concatArrays", listOf(
                            "\$\$value", // 当前累积值
                            Document(
                                "\$filter", Document()
                                    .append("input", "\$\$this") // 遍历当前数组
                                    .append("as", "item")
                                    .append(
                                        "cond", Document(
                                            "\$regexMatch", Document()
                                                .append(
                                                    "input",
                                                    Document("\$ifNull", listOf("\$\$item.name", ""))
                                                ) // 处理 null 值
                                                .append("regex", itemName) // 匹配正则表达式
                                                .append("options", "i") // 忽略大小写
                                        )
                                    )
                            )
                        )
                    )
                )
        )

        // 添加 rotationItems 字段
        val addRotationItemsStage = Aggregation.addFields()
            .addFieldWithValue("rotationItems", reduceOperation)
            .build()

//        // 添加 $project 阶段以选择需要的字段
//        val projectStage = Aggregation.project()
//            .andInclude("mission_location", "mission_name","mission_type_zh","rotation_data") // 替换为您想要的字段名
//            .and("rotationItems").`as`("filteredRotationItems") // 如果需要重命名字段


        // $match 阶段，过滤符合条件的记录
        val matchStage = Aggregation.match(
            Criteria.where("rotationItems").ne(emptyList<Any>()) // 确保 rotationItems 不为空
        )

        // 聚合管道
        val aggregation = Aggregation.newAggregation(
            addFieldsStage,
            addRotationItemsStage,
//            projectStage, // 在这里添加 $project 阶段
            matchStage
        )

        // 执行查询
        return mongoTemplate.aggregate(aggregation, "wf_mission", WfMissionEntity::class.java).mappedResults
    }
}