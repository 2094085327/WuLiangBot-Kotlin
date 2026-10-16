package bot.wuliang.worldstate.source

/** 没有可用新快照或允许回退的旧快照时，对上层暴露统一的可读错误。 */
class WorldStateUnavailableException(cause: Throwable? = null) :
    IllegalStateException("暂时无法获取官方世界状态，请稍后重试", cause)