# cc-biobank

管理生物样本的接收、分装、保管和领用记录。

## 主要业务规则

- **接收样本**：记录唯一外部样本号、样本类型、初始体积、保留体积、保存位置和接收时间。初始体积必须大于零；保留体积不得为负且必须小于初始体积；外部样本号重复返回 `409 DUPLICATE_EXTERNAL_ID`。
- **分装**：一次请求支持两种模式（二选一）：等分（`count` + `volumePerAliquot`）或指定体积列表（`volumes`），可附带本次损耗 `lossVolume`。所有子样本体积与损耗之和不得超过母样本当前可用体积（剩余量 − 保留体积），即扣减后母样本剩余量不得低于保留体积。分装与母样本扣减在同一事务中完成，任一子项失败整体回滚，不留部分结果。
- **领用**：一次请求可从一个或多个子样本扣减体积。请求必须携带业务幂等键 `idempotencyKey`：相同键 + 相同内容重放返回首次的结果；相同键 + 不同内容返回 `409 IDEMPOTENCY_CONFLICT`。并发领用通过行级悲观锁（按子样本 id 排序加锁）保证不出现负库存、不突破保留量、不丢失更新；体积耗尽的子样本进入 `DEPLETED` 状态，不可继续领用。
- **事件与谱系**：每次接收、分装、损耗、领用都会写入不可变事件（`sample_events`，自增 id 即事件顺序），可查询样本完整谱系、各子样本当前体积与事件顺序。失败请求在事务内回滚，不写入任何事件。
- **体积计算**：统一使用 `BigDecimal`，固定 3 位小数精度、`HALF_UP` 舍入（见 `VolumeMath`），禁止浮点金额式计算。
- **错误模型**：统一 JSON 错误结构 `{timestamp, status, code, message, details}`，错误码区分校验失败（`VALIDATION_FAILED`，400）、资源不存在（`NOT_FOUND`，404）、幂等冲突（`IDEMPOTENCY_CONFLICT` / `DUPLICATE_EXTERNAL_ID`，409）和库存不足（`INSUFFICIENT_STOCK`，422）。
- **持久化**：JPA + H2；`samples.external_id` 与 `issue_records.idempotency_key` 设有唯一约束。

## 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/samples` | 接收样本入库 |
| GET | `/api/samples/{id}` | 查询样本当前状态与剩余体积 |
| GET | `/api/samples/{id}/events` | 查询样本全部事件（按顺序） |
| GET | `/api/samples/{id}/lineage` | 查询完整谱系（样本 + 子样本 + 事件） |
| POST | `/api/samples/{id}/aliquots` | 分装创建子样本 |
| GET | `/api/aliquots/{id}` | 查询子样本当前状态 |
| GET | `/api/aliquots/{id}/events` | 查询子样本事件 |
| POST | `/api/issues` | 领用（携带幂等键，可跨多个子样本） |

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
