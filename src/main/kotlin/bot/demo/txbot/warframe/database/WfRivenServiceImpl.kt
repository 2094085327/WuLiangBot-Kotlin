package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.logging.Logger


/**
 * @description: Warframe 词库服务实现类
 * @author Nature Zero
 * @date 2024/5/20 下午11:50
 */
@Service
class WfRivenServiceImpl : ServiceImpl<WfRivenMapper?, WfRivenEntity?>(), WfRivenService {
    @Autowired
    lateinit var rivenMapper: WfRivenMapper

    private val logger: Logger = Logger.getLogger(WfRivenServiceImpl::class.java.getName())


    override fun insertRiven(wfEnRivenList: List<WfRivenEntity>) {
        wfEnRivenList.forEach { enLexicon ->
            rivenMapper.insertIgnore(enLexicon)
        }
        logger.info("紫卡词库更新完成")
    }

    override fun turnKeyToUrlNameByRiven(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>().eq("zh_item_name", zh).or().eq("en_item_name", zh)
        return rivenMapper.selectOne(queryWrapper)
    }
}