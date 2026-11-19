package com.zc.mybackdianping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.mybackdianping.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
