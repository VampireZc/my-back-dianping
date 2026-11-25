package com.zc.mybackdianping.mapper;

import com.zc.mybackdianping.dto.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper{
    List<OrderVO> queryByUserID(
            @Param("userId") Long userId,
            @Param("status") Integer status );

    OrderVO queryByOrderID(
            @Param("userId") Long userId,
            @Param("id") Long id );
}
