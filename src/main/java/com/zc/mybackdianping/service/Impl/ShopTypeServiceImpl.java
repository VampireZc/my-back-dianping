package com.zc.mybackdianping.service.Impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.ShopType;
import com.zc.mybackdianping.mapper.ShopTypeMapper;
import com.zc.mybackdianping.service.IShopTypeService;
import org.springframework.stereotype.Service;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
}
