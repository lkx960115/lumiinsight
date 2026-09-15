# 产品界面标准

新功能、改页面、补展示时，交付的是给品类/运营看的产品，不是给开发看的后台。

**本文件是正本。** 改任何 `apps/web` 页面或样式前必须读完并按这里抄。Cursor 的 `.cursor/rules/product-ui.mdc` 只负责提醒读这里；换工具以本文件和 `AGENTS.md` 为准。

样式只写在 `apps/web/src/assets/main.css`。禁止在单个 `.vue` 里用 scoped CSS 另做一套按钮、表格、弹窗。文案和时间用 `apps/web/src/utils/labels.ts`，禁止页面内再翻译一遍 `READY` / `xiaohongshu`。

---

## 必须

- 一屏一个主操作。其余用次要按钮、文字按钮或「更多」。
- 用户看见中文：平台、状态、类型、结果。禁止把 `READY`、`xiaohongshu`、`FAILED` 直接铺在主界面。
- 先给结论再给明细。图表、状态、有效评论在前；任务 ID、原始报文、处理记录默认收起。
- 主列表默认展示有效评论，不要把重复条当成工作台正文。
- 文案说业务（清洗并分析、有效评论、正向），不说实现（Job、DTO、JSON、Worker）。
- 启用/停用必须能在列表上直接开关，不要只显示「是/否」再逼用户点编辑。

---

## 页面骨架

每个列表页同一套：

```vue
<div class="page-head">
  <div>
    <h2>页面名</h2>
    <p class="muted">一句话说明做什么、下一步是什么。</p>
  </div>
  <div class="page-actions">
    <el-button type="primary">唯一主操作</el-button>
  </div>
</div>
<div class="panel">
  <el-table :data="rows" empty-text="还没有数据。请先……">
    <!-- 列见下方「列表」 -->
  </el-table>
</div>
```

- 一页多段（导入 / 分析）：用 `product-section`，每段一个 `h3`，表仍包在 `panel` 里。
- 项目详情这种多块工作台：用 `el-card.block` 分段，卡内表格仍走全局 `.el-table`，不要再包一层自定义表头。
- 禁止：有的页卡片套表、有的页裸表、有的页再发明时间线。

---

## 按钮

同一区域只允许 **一颗** `type="primary"`。

| 场景 | 写法 | 例子 |
|------|------|------|
| 页头主操作 | `el-button type="primary"` | 新建项目、清洗并分析、新增方面 |
| 页头次要 | 默认按钮，不要 primary | 仅清洗、仅分析、返回列表 |
| 页头弱操作 | `el-button text` | 刷新（放主按钮左侧） |
| 筛选查询 | 默认按钮 | 查询 |
| 筛选重置 | `el-button text` | 重置 |
| 行内操作 | `el-button text`，包在 `div.row-actions` | 编辑、打开、重试 |
| 行内危险 | `el-button text type="danger"` | 删除 |
| 弹窗 | 取消 = 默认；确定 = `type="primary"` | 取消 / 保存 |

禁止：

- 同一行并排两颗实心主按钮
- 行内再用实心主按钮（导入表里的「清洗并分析」用文字按钮）
- 自造圆角、描边、渐变、阴影按钮
- 用 `el-link` / 自定义 `<a class="btn">` 冒充按钮（项目名用 `name-link` 除外）

---

## 列表

列顺序固定，缺的列可省略，**不得换序**：

1. **主对象**（项目名、文件名、评论、用途、操作名）
2. **关键属性**（可多列：品牌、主型号、品类、平台、匹配词、角色）
3. **状态**（开关，或中文：完成 / 失败 / 成功 / 正向）
4. **时间**（有则放，北京时间）
5. **操作**（文字按钮，最后一列）

属性列可以多，但必须排在主对象之后、状态之前。平台必须中文（`platformListLabel`）。

**说明不要单独成列。** 条数、失败原因写在状态下面：

```vue
<el-table-column label="状态" min-width="200">
  <template #default="{ row }">
    <span class="status-text" :class="{ 'is-ok': ok, 'is-bad': bad }">{{ jobStatusLabel(row.status) }}</span>
    <p v-if="note && note !== '—'" class="cell-note">{{ note }}</p>
  </template>
</el-table-column>
```

完成 / 失败 / 成功用 `.status-text`，**不要用 `el-tag` 表示任务状态**。`el-tag` 只用于展开行里的方面明细。

时间：系统记录用 `formatClock`；评论原文时间用 `formatDateTime`。

空表必须写 `empty-text`，告诉下一步做什么，例如「还没有项目。点右上角新建。」

主对象可带一行 `.cell-note`（如「评论 20 条」）。行内链接用 `.name-link`。

---

## 开关

启用列：`el-switch`，`:active-value="1"` `:inactive-value="0"`，列表上直接改，失败要回滚开关。

---

## 筛选条

有筛选的列表，用 `.toolbar`，放在表上方、卡片标题下方。顺序固定：

**筛选项 → 查询 → 重置**

```vue
<div class="toolbar">
  <el-select v-model="platform" clearable placeholder="全部平台" style="width: 140px" @change="search">
    <el-option label="小红书" value="xiaohongshu" />
  </el-select>
  <el-input v-model="keyword" clearable placeholder="搜索原文" style="width: 240px" @keyup.enter="search" />
  <el-button @click="search">查询</el-button>
  <el-button text @click="reset">重置</el-button>
</div>
```

| 动作 | 怎么用 | 不要 |
|------|--------|------|
| **查询** | 默认按钮。按当前条件拉列表，并回到第 1 页 | 不要做成主按钮 |
| **重置** | 文字按钮。清空全部筛选项，立刻重新查询，回到第 1 页 | 不要叫「清空」；不要只清空输入却不查 |
| **清空** | 只出现在输入框/下拉的叉（`clearable`）。清空单个条件后：下拉立即查询；搜索框等回车或点查询 | 不要在查询旁边再放一颗「清空」按钮 |
| **刷新** | 页头文字按钮。条件、页码都不变，只重拉当前数据 | 不要和重置混用；不要做成主按钮；没有自动变化的配置页不必放 |

规则：

- 下拉变更、点清除叉：立即查询（并回第 1 页）。
- 搜索框不要每敲一个字就请求；回车或点查询。
- 没有筛选条的页面不要硬造查询/重置。
- 「查询」和「重置」不要同时做成实心主按钮。
- 未筛时 empty-text 写「还没有…，请先…」；筛过没有结果写「没有符合条件的…。试试重置筛选。」

---

## 刷新

- 文案固定「刷新」，`el-button text`，放 `page-actions`。
- 页头已有主按钮时：刷新放主按钮 **左侧**。
- 适用：任务日志、操作日志、调用记录这类会自己变的数据。
- 刷新保留当前筛选和当前页。
- 保存弹窗成功后的 `load()` 不算刷新按钮，不必再提示「请刷新」。

---

## 分页

所有分页同一套，写在表格正下方（`panel` / 卡片内），右对齐：

```vue
<el-pagination
  class="pager"
  background
  hide-on-single-page
  layout="total, prev, pager, next"
  :page-size="pageSize"
  :total="table.total"
  v-model:current-page="page"
  @current-change="load"
/>
```

| 项 | 规定 |
|----|------|
| 工作台列表（项目、评论） | 每页 **10** 条 |
| 系统管理列表（用户、任务日志、操作日志） | 每页 **20** 条 |
| 只有一页 | 不显示分页（`hide-on-single-page`） |
| 显示 | 用 `total` 显示「共 x 条」，中文环境已开 |
| 换筛选 / 点查询 / 点重置 | 回到第 1 页再请求 |
| 点刷新 / 翻页 | 不重置筛选 |

禁止：

- 每页条数下拉（`sizes`）
- 跳转到第几页（`jumper`）
- 自造「上一页 / 下一页」
- 有的列表 `total > 20` 才渲染、有的一直挂着第 1 页
- 分页和筛选挤在同一行

---

## 加载、空状态、确认、提示

- 拉列表用表格 `v-loading`，不要整页遮罩。
- 主操作进行中给按钮 `:disabled`，防止连点。
- 删除用 `ElMessageBox.confirm`，中文说明，`type="warning"`；不要用浏览器 `confirm`。
- 成功短句：`已保存` / `已删除` / `已提交导入任务`。失败用 `friendlyMessage`，不要抛 Java 异常原文。
- 新建成功回到第 1 页；编辑成功留在当前页。

---

## 弹窗与表单

```vue
<el-dialog v-model="visible" :title="form.id ? '编辑…' : '新建…'" width="560px">
  <el-form label-width="90px">
    <!-- 中文标签 -->
  </el-form>
  <template #footer>
    <el-button @click="visible = false">取消</el-button>
    <el-button type="primary" @click="save">保存</el-button>
  </template>
</el-dialog>
```

宽度常用 `480px` / `520px` / `560px`。不要全屏弹窗、不要底部抽屉当普通编辑。弹窗用「取消」丢弃，不要再做「清空表单」。

---

## 文案与时间（必须用现成函数）

| 用途 | 函数 |
|------|------|
| 平台 | `platformLabel` / `platformListLabel` |
| 任务类型 | `jobTypeLabel` |
| 任务状态 | `jobStatusLabel` |
| 系统时间 | `formatClock` |
| 评论原文时间 | `formatDateTime` |
| 失败/接口说明 | `friendlyMessage` |
| 导入结果 | `importRemark` |
| 分析/清洗结果 | `pipelineRemark` |
| 操作日志动作 | `auditActionLabel` / `auditDetailLabel` |
| 分析用途 | `purposeLabel` |

禁止把 Java 异常、类名、`octet-stream`、模型 JSON、Token 铺给用户。

---

## 禁止

- 同一区域并排多颗实心主按钮
- 用任务 ID、项目数字 ID 当用户要读的第一列
- 为了「功能做完了」把内部字段整表摊开
- 同一产品里有的列表用卡片套表、有的裸表、有的表头还是英文枚举
- 自造时间线、自造状态胶囊、自造一套表格线框
- 在 scoped 样式里改 `.el-table` / `.el-button` / `.el-dialog` / `.el-pagination`
- 分页提供每页条数或跳转到第几页

---

## 对照

- 错：ID / 1 / 分析 / FAILED / Error while extracting response for type [java.util.Map]
- 对：分析 · 演示吸顶灯 · 失败 · 分析服务没有返回结果，请重试 · 2026-09-15 15:48

---

## 改完自检

- [ ] 这一屏是否只有一颗主按钮？
- [ ] 列表是否主对象 → 属性 → 状态 → 时间 → 操作？
- [ ] 平台/状态是否中文？说明是否在状态下方而不是单独一列？
- [ ] 空表是否写了下一步？筛过无结果是否和「还没有」区分开？
- [ ] 有筛选时是否查询 + 重置，清空是否只用输入框叉？
- [ ] 分页是否 `total, prev, pager, next`，单页是否隐藏，有没有 sizes/jumper？
- [ ] 刷新是否文字按钮且不改筛选、不改页码？
- [ ] 时间是否 `formatClock` / `formatDateTime`？
- [ ] 是否把新样式写进了 `main.css` 而不是页面 scoped？
