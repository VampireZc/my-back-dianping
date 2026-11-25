# 需求文档：笔记评论（V1.1 —— 按现有 tb_blog_comments 表修订）

> 提出人：产品经理 & 前端开发（AI）  实现人：后端开发（你）
> 优先级：P1  预计工作量：2 天
> V1.1 变更：数据库不再新建表，直接采用现有 `tb_blog_comments`；接口与功能随之调整

## 1. 背景与目标

笔记详情页的评论区目前是前端写死的假数据（前端已改造完成，见第 6 节）。
本期上线真实评论功能：发表/回复评论（两层楼中楼）、点赞、逻辑删除。

评论是典型的**读多写少 + 高并发追加**场景，要求复用现有 `CacheClient` 做列表缓存
（与订单的版本号手写方案形成对照），点赞用 ZSet 判重 + DB `liked` 列存计数。

## 2. 现有表结构（不动 DDL，直接用）

```sql
tb_blog_comments (
  id         bigint unsigned AUTO_INCREMENT PK,
  user_id    bigint unsigned NOT NULL,          -- 发表人
  blog_id    bigint unsigned NOT NULL,          -- 所属笔记
  parent_id  bigint unsigned NOT NULL,          -- 一级父评论id；一级评论自身为 0（表已内置两层压平设计）
  answer_id  bigint unsigned NOT NULL,          -- 被直接回复的评论id；直接评笔记为 0
  content    varchar(255) NOT NULL,
  liked      int unsigned,                      -- 点赞数（DB 为权威计数）
  status     tinyint unsigned,                  -- 0 正常 / 1 已删除 / 2 待审核
  create_time timestamp, update_time timestamp
)
```

约定（全部由后端实现，前端不感知）：

- **两层压平**：`answer_id` 指向被回复的那条评论；`parent_id` 永远指向它所属的一级评论。
  回复一级评论：`answer_id = 该评论id, parent_id = 该评论id`；回复二级回复：
  `answer_id = 该回复id, parent_id = 该回复的 parent_id`。
  回复 `@nick: ` 前缀由后端拼接进 content。
- **逻辑删除**：删除 = `update status = 1`。**所有查询必须带 `status = 0`**。
- 删除一级评论时其下回复一并 `status = 1`（`where parent_id = ?`）。

## 3. 功能清单

| 编号 | 功能 | 说明 |
|---|---|---|
| F1 | 评论列表 | 一级评论（parent_id=0）按 create_time 正序分页，每页 10 条；每条携带 replyCount + 前 2 条回复 |
| F2 | 展开回复 | 分页拉取某一级评论下全部回复 |
| F3 | 发表评论 | 直接评笔记（answer_id=0） |
| F4 | 回复评论 | 回复任意评论，按第 2 节压平规则落库 |
| F5 | 评论点赞 | 点赞/取消，DB liked ±1，返回新计数 |
| F6 | 删除评论 | 仅作者本人；逻辑删除 |
| F7 | 列表缓存 | 第一页用 CacheClient 缓存，发/删评论时删缓存 |

## 4. 接口定义（前端已按此实现，字段不可变更）

统一前缀 `/comment`，均需登录。

### 4.1 GET `/comment/page?blogId={blogId}&current={current}`

`Result.ok(Page<CommentVO>)`，records 为一级评论（parent_id=0 且 status=0），正序。

**CommentVO：**

| 字段 | 类型 | 来源 |
|---|---|---|
| id / blogId / parentId / answerId / userId / content / liked / createTime | | tb_blog_comments |
| nickName / icon | String | tb_user 联查 |
| isLike | Boolean | Redis ZSet 是否包含当前用户 |
| replyCount | Long | `count(parent_id = id and status = 0)` |
| replies | List\<CommentVO\> | 前 2 条回复（同样含 nickName/icon/isLike） |

### 4.2 GET `/comment/{parentId}/replies?current={current}`

该一级评论下全部回复（parent_id = {parentId} 且 status=0），正序分页。

### 4.3 POST `/comment`

请求体：`{"blogId": 1, "content": "xxx", "answerId": 0}`
（answerId=0 表示直接评笔记；否则为被回复评论的 id，前端**不做压平**，压平是后端职责）。
成功返回 `Result.ok(新评论id)`。校验：content 非空 ≤255；笔记存在；answerId 对应评论存在且 status=0。

### 4.4 PUT `/comment/like/{id}`

点赞/取消点赞。返回 `Result.ok(点赞后 DB liked 值)`。

### 4.5 DELETE `/comment/{id}`

仅作者本人，逻辑删除；否则 `Result.fail("无权删除")`。

## 5. Redis 设计要求

1. **点赞判重**：ZSet `blog:comment:liked:{commentId}`，member=userId、score=时间戳。
   用途只有一个：判断/记录当前用户是否点过赞（ZSCORE 判存在 → 决定 +1 还是 -1）。
   **计数以 DB `liked` 列为准**（表设计如此），更新用 `setSql("liked = liked ± 1")`。
   思考题：为什么这里不用 ZSet card 当计数了？和笔记点赞（blog:liked，纯 ZSet 无 DB 列）的差异在哪？
2. **第一页缓存**：key `cache:comment:page:{blogId}:1`，TTL 30 分钟 + 随机，
   **必须通过现有 `CacheClient` 读取**（queryWithMutex 或 queryWithPassThrough，二选一并能说出理由）。
3. **一致性**：F3 发表、F6 删除成功后 `DELETE cache:comment:page:{blogId}:1`。
   对比思考（写在提交说明里）：为什么评论用"写时删缓存"够了，订单场景却推荐版本号方案？
4. **isLike 陷阱（本需求核心考点）**：缓存里存**不含 isLike** 的裸数据，读出后对当页评论
   批量 ZSCORE 补齐当前用户状态。否则 A 的点赞状态会缓存给 B 看。

## 6. 前端方案（已完成）

`blog-detail.html` 已改造：真实评论区 + 展开收起 + 回复压平预览 + 点赞 + 本人删除 + 底部输入框。
预览：`blog-detail.html?id=9&mock=1`；联调去 mock。

## 7. 验收标准（自测清单）

- [ ] 所有查询过滤 status=0；删除后评论及其回复不再出现（表里行还在）
- [ ] 回复一条二级回复后，它出现在正确的一级评论回复列表里，content 带 `回复 @nick: ` 前缀，parent_id/answer_id 符合压平规则
- [ ] 点赞 +1 / 再点 -1，DB liked 与返回一致；A 的 isLike 不影响 B（陷阱项）
- [ ] 非作者删除返回"无权删除"
- [ ] 发/删评论后第一页缓存 key 消失，下次查询重建且含新评论
- [ ] 第二次查询命中缓存，不同用户各自的 isLike 正确
- [ ] 评论+用户联查、replyCount 批量统计，无 N+1

## 8. 实现提示

- replyCount：`left join (select parent_id, count(*) from tb_blog_comments where status=0 group by parent_id)` 或对当页 10 个 id 一次 `in` 批量统计。
- 前 2 条回复：`where parent_id in (...) and status=0` 批量查出后内存截取，或 MySQL 8 窗口函数。
- 逻辑删除一条 SQL：`update tb_blog_comments set status=1 where id=? or parent_id=?`（删一级时）。
- 缓存 JSON 的 LocalDateTime：用 CacheClient 里现成的 ObjectMapper，别用 hutool（订单需求踩过的坑）。
