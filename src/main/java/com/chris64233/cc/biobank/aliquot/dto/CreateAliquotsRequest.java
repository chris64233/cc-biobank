package com.chris64233.cc.biobank.aliquot.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 两种模式二选一：
 * 1. 等分模式：count + volumePerAliquot；
 * 2. 指定体积模式：volumes 列表。
 * lossVolume 为本次分装损耗，可缺省为 0。
 */
public record CreateAliquotsRequest(
        Integer count,
        BigDecimal volumePerAliquot,
        List<BigDecimal> volumes,
        BigDecimal lossVolume) {
}
