package com.chris64233.cc.biobank.domain;

/**
 * 样本生命周期状态。
 * EXHAUSTED 表示可用体积已耗尽，不可继续领用/分装。
 */
public enum SampleStatus {
    ACTIVE,
    EXHAUSTED
}
