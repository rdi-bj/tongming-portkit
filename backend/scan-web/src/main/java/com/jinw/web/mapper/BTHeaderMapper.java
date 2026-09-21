package com.jinw.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinw.web.domain.BTHeader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BTHeaderMapper extends BaseMapper<BTHeader> {
    @Select("""
        SELECT IFNULL(
            (SELECT 1
             FROM b_t_header
             WHERE LIB_ID = #{libId}
               AND SUPPORT_RISCV = '0'
             LIMIT 1),
            0
        )
    """)
    boolean existsSupportRiscvZero(@Param("libId") String libId);
}
