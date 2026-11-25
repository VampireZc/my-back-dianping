# 需求文档：我的订单（V1.0）

> 提出人：产品经理 & 前端开发（AI）  实现人：后端开发（你）
> 优先级：P1  预计工作量：2~3 天

## 1. 背景与目标

目前用户秒杀下单后，订单"石沉大海"——没有任何页面能看到自己买过什么、也无法支付/核销/取消。
本期目标：上线「我的订单」功能，让用户能查看订单列表、订单详情，并完成订单状态流转（支付→核销 / 取消）。

订单列表是典型的**读多写少 + 多表联查**场景，要求后端引入缓存并保证操作后的缓存一致性。

## 2. 功能清单

| 编号 | 功能 | 说明 |
|---|---|---|
| F1 | 订单列表（分页） | 按状态 Tab 筛选：全部 / 待支付(1) / 待核销(2) / 已完成(3) / 已取消·退款(4,5,6)，按下单时间倒序 |
| F2 | 订单详情 | 单条订单完整信息 |
| F3 | 模拟支付 | 待支付(1) → 已支付(2)，记录 payTime，payType 固定为 1（余额） |
| F4 | 模拟核销 | 已支付(2) → 已核销(3)，记录 useTime |
| F5 | 取消订单 | 待支付(1) → 已取消(4)（已支付的不允许直接取消） |
| F6 | 列表缓存 | 列表第一页做 Redis 缓存，状态变更后保证一致性 |

**订单状态机（必须校验，非法流转直接返回失败）：**

```
1 待支付 ──支付──▶ 2 已支付 ──核销──▶ 3 已核销(终态)
   │
   └──取消──▶ 4 已取消(终态)
```

## 3. 接口定义（前端按此对接，字段不可随意变更）

统一前缀 `/api/voucher-order`，均需登录（走现有 token 拦截器）。

### 3.1 GET `/voucher-order/page?status={status}&current={current}`

- `status`：可选，不传=全部；传 1/2/3；传 4 表示"已关闭"（含 4,5,6 三个状态）
- `current`：页码，默认 1；固定每页 5 条
- 返回 `Result.ok(page)`，page 为 MyBatis-Plus 的 `Page<OrderVO>` 结构（records/total/current/pages）

**OrderVO 字段（多表联查产物）：**

| 字段 | 类型 | 来源 |
|---|---|---|
| id | Long | tb_voucher_order.id |
| status | Integer | tb_voucher_order.status |
| payType | Integer | tb_voucher_order.pay_type |
| createTime | LocalDateTime | tb_voucher_order.create_time（前端格式化） |
| payTime / useTime | LocalDateTime | tb_voucher_order |
| voucherTitle | String | tb_voucher.title |
| payValue | Long | tb_voucher.pay_value（单位：分，前端除100展示） |
| actualValue | Long | tb_voucher.actual_value |
| rules | String | tb_voucher.rules |
| shopId | Long | tb_voucher.shop_id |
| shopName | String | tb_shop.name |
| shopImg | String | tb_shop.images（取逗号分隔的第一张） |
| stock / endTime | - | tb_seckill_voucher（详情页展示券有效期用 endTime） |

联查涉及：`tb_voucher_order ⋈ tb_voucher ⋈ tb_shop ⋈ tb_seckill_voucher`，
建议在 `VoucherOrderMapper.xml` 手写 SQL（学习 XML 多表映射），不要多次单表查再内存拼装。

### 3.2 GET `/voucher-order/{id}`

返回单个 OrderVO。必须校验订单归属（userId == 当前登录用户），否则返回 `Result.fail("无权查看")`。

### 3.3 PUT `/voucher-order/{id}/pay`、`/voucher-order/{id}/use`、`/voucher-order/{id}/cancel`

无请求体。成功返回 `Result.ok()`，状态非法或订单不存在返回 `Result.fail(原因)`。

## 4. Redis 设计要求（本期学习重点）

1. **缓存对象**：仅缓存每个用户 `status=全部/1/2/3/4` 的**第一页**列表（第二页及以后直接查库）。
2. **Key 设计**（参考，可自行调整但要文档化）：
   `cache:order:page:{userId}:{status}` → JSON 字符串，TTL 30 分钟 + 随机值（防雪崩，复用你 CacheClient 的思路）。
3. **一致性方案（二选一，推荐 B）**：
   - 方案 A：订单状态变更后，删除该用户所有 status 的列表 key（5 个 key，或 SCAN 匹配删）。
   - 方案 B：**版本号失效法**——列表 key 中拼入版本号：`cache:order:page:{userId}:{ver}:{status}`，
     版本号存于 `cache:order:ver:{userId}`（INCR 获取）。状态变更后只需 INCR 版本号，旧缓存靠 TTL 自然淘汰，无需批量删除。
4. **注意**：缓存 JSON 时 LocalDateTime 的序列化问题，建议统一用你现有的 JSON 工具并配置 JavaTimeModule。

## 5. 前端方案（我负责实现，你不用管）

- 新增 `order.html` 订单列表页：状态 Tab + 订单卡片（店铺图、券名、金额、状态、时间）+ 操作按钮（去支付/去核销/取消）+ 分页。
- `info.html` 个人中心加「我的订单」入口。
- 前端只依赖第 3 节的接口契约，联调前我会用假数据先把页面写好。

## 6. 验收标准（自测清单）

- [ ] 未登录访问订单接口返回 401
- [ ] 列表按下单时间倒序，Tab 筛选正确，"已关闭"包含状态 4/5/6
- [ ] 只能操作自己的订单（改 URL 中订单 id 访问他人订单被拒绝）
- [ ] 待支付订单可支付、可取消；已支付订单不可取消、可核销；已核销/已取消订单任何操作都失败
- [ ] 支付后 payTime、取消/核销后对应时间字段正确落库
- [ ] 第一次请求列表后 Redis 中出现缓存 key；完成一次支付后再查列表，返回的数据是最新的（一致性生效）
- [ ] 手写 XML SQL 中无 N+1 查询（列表一次 SQL 查出全部字段）

## 7. 实现提示（只给思路不给代码）

- 秒杀下单时你的 `saveSeckillOrder` 没有显式设置 status/payType，确认 DB 表默认值是否为 1/1，不是的话补上。
- 状态流转的 update 建议带状态条件（乐观锁思想）：`update ... set status=2 where id=? and status=1`，防止并发重复支付。
- `Page<OrderVO>` 用 MyBatis-Plus 分页插件 + XML 联查可以直接返回 VO 分页，回忆一下分页插件对多表 SQL 的 count 处理。
- 思考题（选做加分项）：取消订单后，是否要回补 Redis 库存和"一人一单"标记？你的 seckill.lua 里两个判断都在 Redis，取消后如何让用户可以重新下单？
