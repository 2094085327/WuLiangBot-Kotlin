package bot.wuliang.distribute.interfaces

import bot.wuliang.distribute.dto.DirectiveConfigDto

interface DirectiveProvider {
    fun getDirectiveConfig(commandKey: String): DirectiveConfigDto?
}
