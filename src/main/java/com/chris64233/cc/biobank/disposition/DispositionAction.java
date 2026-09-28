package com.chris64233.cc.biobank.disposition;

/**
 * 冻结样本的处置决定。
 */
public enum DispositionAction {
    /** 销毁。 */
    DESTROY,
    /** 返还受试者。 */
    RETURN,
    /** 保留在库但禁止研究使用。 */
    RETAIN
}
