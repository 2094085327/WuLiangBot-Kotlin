package bot.demo.txbot.warframe.vo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty


/**
 * @description: WfUtil Vo层
 * @author Nature Zero
 * @date 2024/8/21 上午9:47
 */
class WfUtilVo {
    data class WfWeather(
        val stateId: Int,
        val dmgStateId: Int,
        var startTime: String
    )

    data class Place(
        val name: String,
        val npc: List<NPC>?
    )

    data class NPC(
        val name: String,
        val excludeIds: List<Int>
    )

    data class ExcludePlace(
        val name: String,
        val excludeIds: List<Int>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SpiralsData(
        val weatherStates: Map<Int, String>,
        val damageTypes: Map<Int, String>,
        @JsonProperty("weather")
        var wfWeather: List<WfWeather>,
        val places: List<Place>,
        val excludePlaces: List<ExcludePlace>
    )
}