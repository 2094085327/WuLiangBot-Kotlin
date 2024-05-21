package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.extension.service.IService

interface WfLexiconService : IService<WfLexiconEntity?> {
    fun setEnLexicon(wfEnLexiconList: List<WfLexiconEntity>)

    fun setZhLexicon(wfLexiconEntity: WfLexiconEntity)
}