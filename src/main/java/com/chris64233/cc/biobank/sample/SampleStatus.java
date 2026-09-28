package com.chris64233.cc.biobank.sample;

public enum SampleStatus {
    /** 在库可用，可继续分装。 */
    AVAILABLE,
    /** 同意撤回后冻结，禁止分装与领用，等待处置决定。 */
    FROZEN,
    /** 已销毁。 */
    DESTROYED,
    /** 已返还受试者。 */
    RETURNED,
    /** 保留在库但禁止研究使用。 */
    RETAINED
}
