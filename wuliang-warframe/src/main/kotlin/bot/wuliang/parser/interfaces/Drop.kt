package bot.wuliang.parser.interfaces

import bot.wuliang.parser.enums.Rarity
import bot.wuliang.parser.enums.Rotation

interface Drop {
    val location: String
    val type: String
    val rarity: Rarity?
    val chance: Double?
    val rotation: Rotation?
    val uniqueName: String?
}