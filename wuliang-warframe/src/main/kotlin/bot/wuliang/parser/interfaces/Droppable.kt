package bot.wuliang.parser.interfaces

import bot.wuliang.parser.enums.Rarity

interface Droppable {
    val rarity: Rarity?
    val probability: Double?
    val drops: List<Drop>?
    val isTradable: Boolean
}