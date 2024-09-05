package bot.demo.txbot.other.vo

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

class HelpVo {
    data class CommandConfig(
        val checkCmd: Boolean,
        val enableAll:Boolean,
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

//class HelpVo {
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    data class CommandConfig(
//        @JsonProperty("checkCmd") val checkCmd: Boolean,
//        @JsonProperty("allCmd") val allCmd: Map<String, CommandGroup>,
//        @JsonProperty("updateMd5") val updateMd5: String
//    )
//
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    data class CommandGroup(
//        @JsonProperty("description") val description: String,
//        @JsonProperty("enable") val enable: Boolean
//    )
//}