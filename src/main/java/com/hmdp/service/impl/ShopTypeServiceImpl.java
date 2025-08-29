package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    String key = CACHE_SHOP_TYPE_KEY;
    @Override
    public Result queryTypeList() {
        // 1. 读缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> list = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(list);
        }

        // 2. 缓存不存在，读数据库
        List<ShopType> list = query()
                .orderByAsc("sort")
                .list();

        // 3.
        // 3. 回写缓存（加 TTL）
        String toCache = cn.hutool.json.JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_TYPE_KEY, toCache, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(list);

    }
}
