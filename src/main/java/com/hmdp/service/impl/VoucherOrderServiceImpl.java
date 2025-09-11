package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 在类初始化的时候取执行线程池
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 获取用户
        Long userId = voucherOrder.getUserId();
        // 2. 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3. 获取锁，这里的加锁是为了兜底
        // 4. 判断获取锁成功
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }

    }

    private  IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 判断结果是否为0
        if (r != 0){
            // 2.1 不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 为0，可以下单，把下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3 订单id
        voucherOrder.setId(orderId);
        // 2.4 用户id
        voucherOrder.setUserId(userId);
        // 2.5 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6 放入阻塞队列中
        orderTasks.add(voucherOrder);   // 这时候用户就有了购买资格

        // 3. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 4. 返回订单id
        return Result.ok(orderId);

    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5. 一人一单
        Long userId = voucherOrder.getUserId();
        // 5.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2 判断是否存在
        if (count > 0){
            // 用户已经购买过了
            log.error("用户已经购买过了");
            return ;
        }
        // 6. 扣减库存，乐观锁解决超卖问题，但是失败率很高，需要改进
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                // .eq("voucher_id", voucherId).eq("stock", voucher.getStock())    // where id = ? and stock = ?
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)    // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足");
            return;
        }
        // 7. 创建订单
        save(voucherOrder);
    }
}
