package bot.wuliang.repository

import bot.wuliang.entity.WfMissionEntity
import org.springframework.data.mongodb.repository.MongoRepository

interface WfMissionRepository : MongoRepository<WfMissionEntity, String> {
}