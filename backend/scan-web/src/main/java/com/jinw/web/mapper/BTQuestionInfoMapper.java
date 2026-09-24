package com.jinw.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinw.web.domain.BTQuestionInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BTQuestionInfoMapper extends BaseMapper<BTQuestionInfo> {
    void insertBatch(@Param("questionInfos") List<BTQuestionInfo> questionInfos);
}
