package com.chris64233.cc.biobank.aliquot;

public enum AliquotStatus {
    /** 在库可领用。 */
    AVAILABLE,
    /** 体积耗尽（历史终态，不可领用、不可处置）。 */
    DEPLETED,
    /** 撤回同意后冻结，等待处置，禁止任何研究领用。 */
    FROZEN,
    /** 处置：已销毁。 */
    DESTROYED,
    /** 处置：已返还受试者。 */
    RETURNED,
    /** 处置：保留实物但永久禁止研究使用。 */
    RETAINED_NO_RESEARCH
}
