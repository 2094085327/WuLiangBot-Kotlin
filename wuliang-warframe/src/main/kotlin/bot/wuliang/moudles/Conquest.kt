 package bot.wuliang.moudles

 import java.time.Instant

 /**
  * 科研任务（深层科研/时光科研）
  * CT_LAB → 深层科研, CT_HEX → 时光科研
  */
 data class Conquest(
     val activation: Instant? = null,
     val expiry: Instant? = null,
     var eta: String? = null,
     val type: String? = null,
     val missions: List<ConquestMission> = emptyList(),
     val variables: List<Info> = emptyList()
 )

 data class ConquestMission(
     val faction: String? = null,
     val missionType: String? = null,
     val difficulty: ConquestDifficulty? = null
 )

 data class ConquestDifficulty(
     val deviation: Info? = null,
     val risks: List<Info> = emptyList()
 )
