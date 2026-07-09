 package bot.wuliang.moudles

 import java.time.Instant

 /**
  * 1999 日历季节
  */
 data class CalendarSeason(
     val activation: Instant? = null,
     val expiry: Instant? = null,
     var eta: String? = null,
     val season: String? = null,
     val seasonKey: String? = null,
     val days: List<CalendarDay> = emptyList()
 )

 data class CalendarDay(
     val day: Int? = null,
     val date: String? = null,
     val type: String? = null,
     val typeKey: String? = null,
     val items: List<Info> = emptyList(),
     val birthday: String? = null
 )
