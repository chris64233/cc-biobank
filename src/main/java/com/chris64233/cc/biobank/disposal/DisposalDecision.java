package com.chris64233.cc.biobank.disposal;

/**
 * 冻结样本的处置决定。
 */
public enum DisposalDecision {
    /** 销毁：终态 DESTROYED，体积台账清零。 */
    DESTROY,
    /** 返还受试者：终态 RETURNED，体积台账清零。 */
    RETURN,
    /** 保留实物但永久禁止研究使用：终态 RETAINED_NO_RESEARCH，体积保留。 */
    RETAIN_NO_RESEARCH
}
