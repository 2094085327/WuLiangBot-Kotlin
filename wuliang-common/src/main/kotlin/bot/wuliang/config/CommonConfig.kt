package bot.wuliang.config

object CommonConfig {
    /**资源路径*/
    const val RESOURCES_PATH = "resources"

    /**图片缓存路径*/
    const val IMG_CACHE_PATH = "$RESOURCES_PATH/imageCache"

    /**文件缓存路径*/
    const val FILE_CACHE_PATH = "$RESOURCES_PATH/fileCache"

    /**其他资源路径*/
    const val RESOURCES_OTHER = "$RESOURCES_PATH/others"

    /**进程pid路径*/
    const val APP_PID_PATH = "$RESOURCES_PATH/app.pid"

    /**帮助配置文件路径*/
    const val HELP_JSON = "$RESOURCES_OTHER/help.json"

    /**日活数据路径*/
    const val DAILY_ACTIVE_PATH = "$RESOURCES_OTHER/daily_active.json"

    /**重启配置路径*/
    const val RESTART_CONFIG = "$RESOURCES_OTHER/restart_config.json"

    /**缓存最大大小*/
    const val MAX_SIZE_MB = 100.0

    /**删除百分比*/
    const val DELETE_PERCENTAGE = 0.5

    const val USER_TICKET_KEY = "Wuliang:userTicket:"

    const val BOT_CONFIG_KEY = "Wuliang:botConfig:"
}