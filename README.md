# cc-biobank

管理生物样本的接收、分装、保管、领用，以及受试者同意（知情同意书）的登记、撤回、样本冻结与处置。

## 核心模型

- **受试者（Subject）**：样本与一切操作的归集主体，编号唯一。
- **同意版本（ConsentVersion）**：受试者某一版知情同意书，含允许用途清单（如 `RESEARCH_STORAGE` 研究保藏、`RESEARCH_USE` 研究使用）、生效时间、失效时间、撤回时间。受试者 + 版本号唯一。
- **原始样本（Sample）/ 后代分装（Aliquot）**：均冗余 `subjectId` 与接收/分装时采用的 `consentVersionId`；分装自动继承母样本的受试者与同意版本。
- **状态机**：
  - 样本：`AVAILABLE → FROZEN → DESTROYED / RETURNED / RETAINED`
  - 分装：`AVAILABLE → DEPLETED`（耗尽）或 `AVAILABLE → FROZEN → DESTROYED / RETURNED / RETAINED`

## 主要业务规则

### 同意关联与领用快照

- **接收样本**必须指定受试者与当时实际采用的同意版本；该同意必须处于有效期、未撤回且允许 `RESEARCH_STORAGE`（研究保藏）用途，否则返回 `422 CONSENT_NOT_VALID`。
- **分装**自动继承母样本的受试者与同意版本。
- **领用**请求必须携带受试者编号、本次用途（`purpose`）与实际采用的同意版本；系统在加锁的同意行上校验其当前有效且允许该用途。
  - 已撤回、已过期、未生效或用途不匹配的同意**不能支持新的领用**，返回 `422 CONSENT_NOT_VALID`。
  - 一次领用只能涉及同一受试者的分装，否则 `400 VALIDATION_FAILED`。
  - 领用时永久保存**同意版本快照**（`consentSnapshot`，含版本、用途、允许清单、有效期、当时撤回状态），日后同意撤回或版本变更都不影响已完成领用的快照。

### 撤回与一次性冻结

- 撤回请求携带业务撤回号 `withdrawalNo`，**幂等**：同号重放返回首次冻结范围，不重复冻结、不重复写事件。
- 撤回在一个事务内：锁定受试者 → 撤回其全部同意版本 → 冻结该受试者**全部仍在库且可用（AVAILABLE）的原始样本与后代分装**。
  - 已耗尽（`DEPLETED`）、已处置的对象不改写。
  - **已经完成的历史领用不被改写**，仍可在受试者历史中查询。
  - 冻结后：新领用、新分装、新接收一律被拒绝。
- **并发互斥**：正在审批的领用与撤回并发时，加锁顺序统一为「受试者 → 同意版本 → 原始样本 → 分装」，二者在受试者行上串行化，结果只能是**完整领用**或**完整冻结**之一，不会出现部分领用 + 冻结（见 `WithdrawalConcurrencyTests`，连续 24 轮并发断言两种结果且每轮恰为其一）。

### 冻结样本处置

- 处置决定（`action`）三种：
  - `DESTROY` 销毁、`RETURN` 返还：对象状态转入终态，在库剩余体积清零。
  - `RETAIN` 保留：对象留在库中、体积不变，但标记为 `RETAINED`，**禁止研究使用**。
- 处置对象可为原始样本（`SAMPLE`）或分装（`ALIQUOT`），可批量；必须同属一个受试者且当前为 `FROZEN`。
- **原子批量**：所有对象的状态与体积台账在同一事务更新，任一对象不合法（未冻结、属于他人、重复对象等）整体回滚，不会只处置一部分。
- **不得重复处置**：已 `DESTROYED/RETURNED/RETAINED` 的对象再次处置返回 `409 ALREADY_DISPOSED`；耗尽（无在库物质）的对象不可处置。
- 处置号 `dispositionNo` **幂等**：同号 + 重放返回首次结果；换号重复处置同一对象被拒。

### 事件、谱系与体积

- 每次接收、分装、损耗、领用、冻结、处置都写入不可变事件（`sample_events`，自增 id 即事件顺序）；失败请求事务回滚，不写任何事件。
- 体积统一 `BigDecimal`，固定 3 位小数、`HALF_UP`（见 `VolumeMath`），禁止浮点式计算。

### 幂等键

- 领用 `idempotencyKey`、撤回 `withdrawalNo`、处置 `dispositionNo` 均为业务幂等键，库内唯一约束兜底并发：
  - 相同键 + 相同内容重放 → 返回首次结果；
  - 相同键 + 不同内容 → `409 IDEMPOTENCY_CONFLICT`。

## 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/subjects` | 登记受试者 |
| GET | `/api/subjects/{id}` | 查询受试者 |
| POST | `/api/subjects/consents` | 登记同意版本（受试者编号 + 版本 + 用途 + 有效期） |
| GET | `/api/subjects/{id}/consents` | 查询受试者全部同意版本 |
| POST | `/api/samples` | 接收样本入库（须受试者 + 同意版本） |
| GET | `/api/samples/{id}` | 查询样本当前状态与剩余体积 |
| GET | `/api/samples/{id}/events` | 查询样本全部事件 |
| GET | `/api/samples/{id}/lineage` | 查询样本谱系（样本 + 分装 + 事件） |
| POST | `/api/samples/{id}/aliquots` | 分装创建子样本 |
| GET | `/api/aliquots/{id}` | 查询子样本当前状态 |
| GET | `/api/aliquots/{id}/events` | 查询子样本事件 |
| POST | `/api/issues` | 领用（受试者 + 用途 + 同意版本 + 幂等键，可跨多个分装） |
| POST | `/api/withdrawals` | 撤回同意并一次性冻结（撤回号幂等） |
| POST | `/api/dispositions` | 批量处置冻结样本（处置号幂等） |
| GET | `/api/subjects/{id}/lineage` | **受试者维度**样本谱系（同意 + 原始样本 + 全部分装 + 事件） |
| GET | `/api/subjects/{id}/freezes` | **受试者维度**撤回与冻结范围历史 |
| GET | `/api/subjects/{id}/issues` | **受试者维度**历史领用（含同意快照） |
| GET | `/api/subjects/{id}/dispositions` | **受试者维度**处置进度 |

### 典型请求体

登记同意版本：

```json
{ "subjectCode": "SUBJ-001", "version": "v1",
  "allowedPurposes": ["RESEARCH_STORAGE", "RESEARCH_USE"] }
```

领用：

```json
{ "idempotencyKey": "ISSUE-20260928-001", "subjectCode": "SUBJ-001",
  "purpose": "RESEARCH_USE", "consentVersion": "v1",
  "items": [ { "aliquotId": 12, "volume": 5.000 } ] }
```

撤回：

```json
{ "withdrawalNo": "WD-20260928-001", "subjectCode": "SUBJ-001", "reason": "受试者撤回" }
```

处置：

```json
{ "dispositionNo": "DP-20260928-001", "subjectCode": "SUBJ-001",
  "action": "DESTROY",
  "items": [ { "targetType": "ALIQUOT", "targetId": 12 } ] }
```

## 错误码

统一 JSON 错误结构 `{timestamp, status, code, message, details}`：

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_FAILED` / `INVALID_REQUEST` | 400 | 参数校验失败 / 请求体无法解析 |
| `NOT_FOUND` | 404 | 受试者、样本、分装等资源不存在 |
| `DUPLICATE_EXTERNAL_ID` | 409 | 外部样本号（或受试者编号）重复 |
| `DUPLICATE_CONSENT` | 409 | 同意版本重复 |
| `IDEMPOTENCY_CONFLICT` | 409 | 幂等键已用但内容不同 / 并发冲突 |
| `SAMPLE_FROZEN` | 409 | 对象已冻结（或未冻结却要求处置），操作不允许 |
| `ALREADY_DISPOSED` | 409 | 对象已处置，不得重复处置 / 领用 |
| `INSUFFICIENT_STOCK` | 422 | 库存不足或分装体积超限 |
| `CONSENT_NOT_VALID` | 422 | 同意不存在、未生效、已过期、已撤回或用途不匹配 |

## 持久化

JPA + H2。唯一约束：`samples.external_id`、`subjects.subject_code`、
`consent_versions(subject_id, version)`、`issue_records.idempotency_key`、
`withdrawal_records.withdrawal_no`、`disposition_records.disposition_no`。
并发控制依赖行级悲观锁（`PESSIMISTIC_WRITE`，统一按 id 升序加锁）。

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
