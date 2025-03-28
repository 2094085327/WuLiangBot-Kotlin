package bot.wuliang.event

import org.springframework.context.ApplicationEvent

/**
 * 资源更新事件
 */
open class ResourceUpdateEvent(source: Any) : ApplicationEvent(source)