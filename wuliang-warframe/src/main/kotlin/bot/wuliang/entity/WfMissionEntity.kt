package bot.wuliang.entity

import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Data
import lombok.NoArgsConstructor
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field


/**
 * @description: TODO
 * @author Nature Zero
 * @date 2024/11/24 23:31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wf_mission") //通过collection参数指定当前实体类对应的文档
class WfMissionEntity {
    @Id //用来标识主键
    var id: String? = null

    @Field("mission_location")
    var missionLocation: String? = null

    @Field("mission_name")
    var missionName: String? = null

    @Field("mission_type") //给字段起别名
    var missionType: String? = null //@Indexed 用于声明字段需要索引

    @Field("mission_type_zh")
    var missionTypeZh: String? = null

    @Field("rotation_data")
    var rotationData: Map<String, MutableList<Map<String, String>>>? = null


}