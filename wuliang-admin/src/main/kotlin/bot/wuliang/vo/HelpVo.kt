package bot.wuliang.vo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

class HelpVo {
    data class CommandConfig(
        val checkCmd: Boolean,
        val enableAll: Boolean,
        val allCmd: Map<String, CommandGroup>,
        var updateMd5: String
    )

    @JsonIgnoreProperties("enable") // 忽略所有 CommandGroup 中的 enable 字段
    data class CommandGroup(
        val description: String?,
        val commendList: List<Command>
    )

    data class Command(
        val command: String,
        val description: String,
        val detail: String,
        val regex: String,
        val enable: Boolean
    )
}