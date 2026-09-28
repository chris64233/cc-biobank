package com.chris64233.cc.biobank.aliquot;

public enum AliquotStatus {
    /** 在库可用，可领用。 */
    AVAILABLE,
    /** 体积耗尽。 */
    DEPLETED,
    /** 同意撤回后冻结，禁止领用，等待处置决定。 */
    FROZEN,
    /** 已销毁。 */
    DESTROYED,
    /** 已返还受试者。 */
    RETURNED,
    /** 保留在库但禁止研究使用。 */
    RETAINED
}
