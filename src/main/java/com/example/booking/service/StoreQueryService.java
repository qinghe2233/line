package com.example.booking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.common.CacheNames;
import com.example.booking.domain.ServiceItem;
import com.example.booking.domain.Store;
import com.example.booking.dto.ServiceItemVO;
import com.example.booking.dto.StoreDetailVO;
import com.example.booking.mapper.ServiceItemMapper;
import com.example.booking.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 门店查询。
 * 门店详情是典型的"读多写极少"数据，缓存收益高、不一致风险低。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreQueryService {

    private static final int STORE_STATUS_OPEN = 1;
    private static final int SERVICE_STATUS_ON_SHELF = 1;

    private final StoreMapper storeMapper;
    private final ServiceItemMapper serviceItemMapper;

    /**
     * 营业中门店列表。
     * 门店数量是个位数，查询走 idx_city_status，不加缓存，
     * 避免"后台改了门店状态、前台却要等缓存过期"的一致性问题。
     */
    public List<StoreDetailVO> listOpen() {
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .eq(Store::getStatus, STORE_STATUS_OPEN)
                        .orderByAsc(Store::getId))
                .stream()
                .map(this::toDetail)
                .toList();
    }

    /** 门店已上架的服务项，走 idx_store_status_sort，直接按 sort_no 排好 */
    public List<ServiceItemVO> listOnShelfServices(Long storeId) {
        return serviceItemMapper.selectList(Wrappers.<ServiceItem>lambdaQuery()
                        .eq(ServiceItem::getStoreId, storeId)
                        .eq(ServiceItem::getStatus, SERVICE_STATUS_ON_SHELF)
                        .orderByAsc(ServiceItem::getSortNo)
                        .orderByAsc(ServiceItem::getId))
                .stream()
                .map(ServiceItemVO::of)
                .toList();
    }

    /**
     * Key = booking:cache:v1:store:detail:{storeId}
     *
     * sync = true：缓存未命中时加本地锁，只放一个线程查库，其余线程等它写缓存后直接命中，
     * 防止热点门店失效瞬间把请求全部打到 MySQL（击穿）。
     * 单机方案够用；多实例部署时每个实例各有一把锁，需改用 Redis SETNX 或逻辑过期，
     * 本项目不引入分布式锁，先讲清单机上限。
     *
     * 查不到返回 null：配合 cacheNullValues，会写入 60 秒的空值标记，
     * 后续同一个不存在的 storeId 直接命中缓存（穿透防护）。
     */
    @Cacheable(cacheNames = CacheNames.STORE_DETAIL, key = "#storeId", sync = true)
    public StoreDetailVO getDetail(Long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            log.info("门店不存在，写入空值缓存防穿透 storeId={}", storeId);
            return null;
        }
        return toDetail(store);
    }

    private StoreDetailVO toDetail(Store store) {
        return new StoreDetailVO(
                store.getId(),
                store.getName(),
                store.getCity(),
                store.getAddress(),
                store.getPhone(),
                store.getBusinessStart(),
                store.getBusinessEnd(),
                store.getStatus());
    }
}
