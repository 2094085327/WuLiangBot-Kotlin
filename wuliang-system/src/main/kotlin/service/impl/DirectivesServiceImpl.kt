package service.impl

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import entity.DirectivesEntity
import mapper.DirectivesMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import service.DirectivesService

@Service
class DirectivesServiceImpl : ServiceImpl<DirectivesMapper?, DirectivesEntity?>(), DirectivesService {
    @Autowired
    lateinit var directivesMapper: DirectivesMapper
    override fun selectDirectivesList(): MutableList<DirectivesEntity?>? {
        return directivesMapper.selectList(null)
    }

    override fun addDirectives(directivesEntity: DirectivesEntity): Int {
        return directivesMapper.insert(directivesEntity)
    }
}