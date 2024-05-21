package bot.demo.txbot.warframe.database

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
class WfLexiconServiceImpl : ServiceImpl<WfLexiconMapper?, WfLexiconEntity?>(), WfLexiconService {
    @Autowired
    lateinit var lexiconMapper: WfLexiconMapper

    private val logger: Logger = Logger.getLogger(WfLexiconServiceImpl::class.java.getName())


    override fun setEnLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        wfEnLexiconList.forEach { enLexicon ->
            lexiconMapper.insertIgnore(enLexicon)
        }
        logger.info("词库更新完成")
    }

    override fun setZhLexicon(wfLexiconEntity: WfLexiconEntity) {
    }
}