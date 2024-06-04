package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


/**
 * @description: Warframe 词库服务实现类
 * @author Nature Zero
 * @date 2024/5/20 下午11:50
 */
@Service
class WfRivenServiceImpl : ServiceImpl<WfRivenMapper?, WfRivenEntity?>(), WfRivenService {
    @Autowired
    lateinit var rivenMapper: WfRivenMapper

    override fun insertRiven(wfEnRivenList: List<WfRivenEntity>) {
        wfEnRivenList.forEach { enLexicon ->
            rivenMapper.insertIgnore(enLexicon)
        }
    }

    override fun turnKeyToUrlNameByRiven(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .eq("zh", zh)
            .eq("attributes", 0)
            .or()
            .eq("en", zh)
            .eq("attributes", 0)
        return rivenMapper.selectOne(queryWrapper)
    }

    override fun turnKeyToUrlNameByLich(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .eq("zh", zh)
            .eq("attributes", 2)
            .or()
            .eq("en", zh)
            .eq("attributes", 2)
        return rivenMapper.selectOne(queryWrapper)
    }

    override fun turnKeyToUrlNameByRivenLike(zh: String): List<WfRivenEntity?>? {
        val regex = zh.toCharArray().joinToString(".*") { it.toString() }  // 将输入字符串转换为.*分隔的正则表达式
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .apply("zh REGEXP {0}", regex)
            .eq("attributes", 1)
            .or()
            .like("en", "%$zh%")
            .eq("attributes", 1)
        return rivenMapper.selectList(queryWrapper)
    }

    override fun turnUrlNameToKeyByRiven(urlName: String): String {
        val queryWrapper = QueryWrapper<WfRivenEntity>().eq("url_name", urlName)
        return rivenMapper.selectOne(queryWrapper)?.zhName ?: ""
    }

    override fun superFuzzyQuery(key: String): List<WfRivenEntity?>? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .like("zh", "%$key%")
            .or()
            .like("en", "%$key%")
        return rivenMapper.selectList(queryWrapper)
    }
}