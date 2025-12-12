package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import lombok.Getter
import lombok.Setter


/**
 * @TableName wf_mission_type
 * @author Nature Zero
 * @date 2024/11/24 00:03
 */
@Getter
@Setter
@TableName("wf_mission_type")
class WfMissionTypeEntity {
    /**
     * 任务类型ID
     */
    @TableId(value = "id")
     val id: Int? = null

    /**
     * 任务类型
     */
    @TableField(value = "mission_type")
    val missionType: String? = null

    /**
     * 中文任务类型
     */
    @TableField(value = "mission_name")
     val missionName: String? = null
}