# cc-biobank

管理生物样本的接收、分装、保管、领用，以及受试者撤回同意后的冻结、处置与全程追踪。

## 主要业务规则

### 样本与同意

- **受试者与同意版本**：样本必须归属于一个受试者，并在接收时固化一个当前有效（未撤回、在有效期窗口内）的同意版本号快照。分装产生的后代样本自动继承母样本的同意版本号。
- **同意用途**：每个同意版本声明允许的研究用途代码集合与有效期（`validFrom` 起、`validUntil` 止，不含截止时刻；`validUntil` 为空表示长期有效，但仍可被撤回）。
- **领用同意校验**：领用请求必须携带受试者编号、实际采用的同意版本号与用途。系统在锁定分装行后校验：同意属于该受试者、未撤回、未过期、用途匹配。**过期、撤回或用途不匹配的同意不能支持新的领用**（`422 CONSENT_INVALID`）。
- **同意快照**：领用幂等记录中固化受试者、同意版本号、用途快照。同意事后撤回或过期**不会改写已经完成的历史领用**；同一幂等键重放永远返回首次结果。

### 撤回与冻结

- **一次性冻结**：撤回请求按撤回号幂等，一次性冻结受试者名下仍在库（`AVAILABLE`）的全部原始样本与后代分装。耗尽（`DEPLETED`）与已完成领用的历史不动。冻结后原始样本不可再分装，分装不可再领用。
- **并发语义**：正在审批的领用与撤回并发时，**只能形成完整领用或完整冻结其中一种结果**。两侧都按 id 升序加行锁（原始样本 → 分装，分装按 aliquot id 升序），并在锁内校验同意行：领用先提交则扣减完整生效、撤回随后冻结剩余库存；撤回先生效则领用整笔回滚，不写任何领用事件，绝不出现"扣了量却被冻结"或"半领用半冻结"。
- **撤回范围**：可撤回受试者全部同意版本（`consentVersionCode` 留空）或指定版本；撤回号重放返回首次冻结范围，不重复冻结。

### 处置

- **处置决定**：仅对已冻结对象可执行 `DESTROY`（销毁，台账清零）、`RETURN`（返还受试者，台账清零）、`RETAIN_NO_RESEARCH`（保留实物但永久禁止研究使用，体积保留）。
- **批量原子性**：一批对象（可同时含原始样本与分装）在单个事务内锁定、逐只校验、统一更新状态与体积台账并写事件/明细；任一对象不满足（不属于该受试者、未冻结、已耗尽、已销毁/返还/保留禁用）则**整批回滚，不会只处置一部分**。
- **终态防重**：耗尽或已经销毁/返还/保留禁用的对象不得重复处置（`409 SAMPLE_NOT_DISPOSABLE`）。处置号幂等，重放返回首次结果。

### 事件、谱系与查询

- **不可变事件**：接收、分装、损耗、领用、样本/分装冻结、样本/分装处置都会写入 `sample_events`（自增 id 即事件顺序），失败请求在事务内回滚，不写入任何事件。
- **受试者维度总览**：`GET /api/subjects/{code}/overview` 一次性聚合同意版本、样本谱系（原始样本 + 全部分装当前状态/体积）、历次撤回的冻结范围、历史领用（含同意快照）与处置批次；另有撤回列表与处置进度（逐对象）查询。
- **体积计算**：统一使用 `BigDecimal`，固定 3 位小数精度、`HALF_UP` 舍入（见 `VolumeMath`），禁止浮点金额式计算。
- **错误模型**：统一 JSON 错误结构 `{timestamp, status, code, message, details}`。错误码：`VALIDATION_FAILED`（400）、`NOT_FOUND`（404）、`DUPLICATE_EXTERNAL_ID` / `DUPLICATE_SUBJECT_CODE` / `DUPLICATE_CONSENT_VERSION` / `IDEMPOTENCY_CONFLICT` / `SAMPLE_NOT_DISPOSABLE`（409）、`INSUFFICIENT_STOCK` / `CONSENT_INVALID`（422）。
- **持久化**：JPA + H2；外部样本号、受试者编号、（受试者+同意版本号）、领用/撤回/处置幂等键均设有唯一约束。

## 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/subjects` | 登记受试者 |
| GET | `/api/subjects/{code}` | 查询受试者 |
| POST | `/api/subjects/{code}/consents` | 登记同意版本（用途集合 + 有效期） |
| GET | `/api/subjects/{code}/consents` | 查询同意版本列表 |
| POST | `/api/samples` | 接收样本入库（携带 subjectCode + consentVersionCode） |
| GET | `/api/samples/{id}` | 查询样本当前状态、剩余体积与同意快照 |
| GET | `/api/samples/{id}/events` | 查询样本全部事件（按顺序） |
| GET | `/api/samples/{id}/lineage` | 查询完整谱系（样本 + 子样本 + 事件） |
| POST | `/api/samples/{id}/aliquots` | 分装创建子样本（继承同意版本号） |
| GET | `/api/aliquots/{id}` | 查询子样本当前状态 |
| GET | `/api/aliquots/{id}/events` | 查询子样本事件 |
| POST | `/api/issues` | 领用（幂等键 + 受试者 + 同意版本 + 用途，可跨多个子样本） |
| POST | `/api/subjects/{code}/withdrawals` | 撤回同意并一次性冻结全部在库样本 |
| GET | `/api/subjects/{code}/withdrawals` | 查询撤回记录与各次冻结范围 |
| POST | `/api/subjects/{code}/disposals` | 对冻结对象批量处置（销毁/返还/保留禁用） |
| GET | `/api/subjects/{code}/disposals` | 查询处置进度（逐对象明细） |
| GET | `/api/subjects/{code}/overview` | 受试者维度总览（谱系+冻结+领用+处置） |

### 请求示例

登记同意版本：

```json
POST /api/subjects/S-1001/consents
{"versionCode":"v1","allowedPurposes":["RESEARCH_GENOMICS"],
 "validFrom":"2026-01-01T00:00:00Z","validUntil":null}
```

领用（携带实际采用的同意版本与用途）：

```json
POST /api/issues
{"idempotencyKey":"ISS-20260928-001","subjectCode":"S-1001",
 "consentVersionCode":"v1","purpose":"RESEARCH_GENOMICS",
 "items":[{"aliquotId":12,"volume":10.5}]}
```

撤回（撤回号幂等，一次性冻结全部在库样本）：

```json
POST /api/subjects/S-1001/withdrawals
{"withdrawalKey":"WD-20260928-001","consentVersionCode":null,"reason":"participant request"}
```

批量处置（对象可为 SAMPLE 或 ALIQUOT，整批原子）：

```json
POST /api/subjects/S-1001/disposals
{"disposalKey":"DSP-20260928-001","decision":"DESTROY",
 "items":[{"targetType":"ALIQUOT","targetId":12},
          {"targetType":"SAMPLE","targetId":7}]}
```

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test
