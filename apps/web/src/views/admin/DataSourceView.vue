<template>
  <div>
    <div class="page-head">
      <div>
        <h2>数据服务</h2>
        <p class="muted">
          第三方按条计费取数。评价原文直接从服务商拉，Token 加密保存，页面只显示后四位。每天自动同步一次，花费达到日预算就停。
        </p>
      </div>
      <div class="page-actions">
        <el-button text @click="load">刷新</el-button>
        <el-button v-permission="'admin:datasource:edit'" type="primary" @click="openProvider()">新增数据服务</el-button>
      </div>
    </div>

    <div class="panel">
      <el-table :data="providers" empty-text="还没有数据服务。点右上角新增，填服务商地址和 Token。">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <div>{{ row.name }}</div>
            <p class="cell-note">{{ row.remark || '按条计费' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="接口地址" min-width="220">
          <template #default="{ row }">
            <div class="muted">{{ row.baseUrl }}</div>
            <p class="cell-note">{{ channelSummary(row) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="单价与预算" min-width="170">
          <template #default="{ row }">
            <div>{{ money(row.pricePerItem) }} / 条</div>
            <p class="cell-note">日预算 {{ row.dailyBudgetYuan > 0 ? money(row.dailyBudgetYuan) + ' 元' : '不限' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="今日用量" min-width="200">
          <template #default="{ row }">
            <div>{{ row.todayItemCount }} 条 · {{ money(row.todayCostYuan) }} 元</div>
            <p class="cell-note">{{ remainText(row) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="170">
          <template #default="{ row }">
            <div class="ds-status">
              <el-switch
                :model-value="row.enabled"
                :active-value="1"
                :inactive-value="0"
                :disabled="!canEdit"
                @change="(val: number) => toggleProvider(row, val)"
              />
              <span class="status-text" :class="row.hasKey ? 'is-ok' : 'is-bad'">
                {{ row.hasKey ? row.apiKeyMasked : '未填 Token' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'admin:datasource:edit'" text @click="openProvider(row)">编辑</el-button>
              <el-button v-permission="'admin:datasource:edit'" text @click="openChannel(row)">配平台</el-button>
              <el-button v-permission="'admin:datasource:edit'" text @click="openTest(row)">测连通</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="product-section">
      <h3>先怎么填</h3>
      <div class="panel" style="padding: 16px 20px">
        <p class="muted">
          以 Just One API 为例：接口地址填 <span class="ds-mono">https://api.justoneapi.com</span>，京东评论的路径是
          <span class="ds-mono">/api/jd/get-item-comments/v1</span>，参数模板填
          <span class="ds-mono">itemId=${productId}&amp;page=${page}</span>，列表路径填
          <span class="ds-mono">$.data</span>。字段映射决定哪一列是原文、哪一列是评价ID，填完点「测连通」，
          系统会真拉第 1 页，把每个字段取到的样子列出来。测一次会按条计费，通常几分钱。
        </p>
        <p class="muted">
          评价ID 必须稳定，否则同一批评论每天重复拉、重复扣钱。列表路径和字段映射都在「配平台」里改，换服务商只改配置，不用改代码。
        </p>
      </div>
    </div>

    <el-dialog v-model="pVisible" :title="pForm.id ? '编辑数据服务' : '新增数据服务'" width="600px">
      <el-form label-width="128px">
        <el-form-item label="名称">
          <el-input v-model="pForm.name" placeholder="如：Just One API" />
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="pForm.baseUrl" placeholder="https://api.justoneapi.com" />
          <div class="muted">只填到域名，具体路径在「配平台」里填</div>
        </el-form-item>
        <el-form-item label="Token">
          <el-input
            v-model="pForm.apiKey"
            type="password"
            show-password
            :placeholder="pForm.id ? '留空则不改已保存的 Token' : '不会在页面回显明文'"
          />
        </el-form-item>
        <el-form-item label="单价">
          <el-input v-model="pForm.pricePerItem" placeholder="0.015" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">元/条，用于算花费</span>
        </el-form-item>
        <el-form-item label="日预算">
          <el-input v-model="pForm.dailyBudgetYuan" placeholder="50" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">元，填 0 表示不限</span>
        </el-form-item>
        <el-form-item label="每天条数上限">
          <el-input v-model="pForm.dailyItemLimit" placeholder="5000" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">填 0 表示不限</span>
        </el-form-item>
        <el-form-item label="单次最多翻页">
          <el-input v-model="pForm.maxPagesPerRun" placeholder="20" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">控制一次同步最多花多少</span>
        </el-form-item>
        <el-form-item label="超时">
          <el-input v-model="pForm.timeoutMs" placeholder="120000" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">毫秒，服务商建议 120000</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="pForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="pForm.remark" placeholder="如：四平台评价，按条计费" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cVisible" title="配置平台接口" width="640px">
      <div v-if="currentProvider?.channels?.length" class="ds-existing">
        <span class="muted">已配通道（点一条可直接改）：</span>
        <el-tag
          v-for="c in currentProvider.channels"
          :key="c.id"
          class="ds-tag"
          effect="plain"
          :type="c.platform === cForm.platform ? 'success' : 'info'"
          @click="editChannel(c)"
        >
          {{ platformLabel(c.platform) }} · {{ c.endpointPath }}
        </el-tag>
      </div>
      <el-form label-width="108px">
        <el-form-item label="平台">
          <el-select v-model="cForm.platform" style="width: 100%" placeholder="选择平台">
            <el-option v-for="item in PLATFORMS" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口路径">
          <el-input v-model="cForm.endpointPath" placeholder="/api/jd/get-item-comments/v1" />
        </el-form-item>
        <el-form-item label="参数模板">
          <el-input v-model="cForm.requestTemplate" placeholder="itemId=${productId}&page=${page}" />
          <div class="muted">可用占位：商品ID、页码、起始时间；Token 由系统自动附加</div>
        </el-form-item>
        <el-form-item label="列表路径">
          <el-input v-model="cForm.itemsPath" placeholder="$.data" />
          <div class="muted">返回里哪一节是评价数组，如 $.data 或 $.data.list</div>
        </el-form-item>
        <el-form-item label="字段映射">
          <el-input v-model="cForm.reviewMapping" type="textarea" :rows="7" placeholder='{"reviewId":"$.id","content":"$.content"}' />
          <div class="muted">左边是系统字段，右边是返回里的路径。reviewId 和 content 必须命中</div>
        </el-form-item>
        <el-form-item label="每页条数">
          <el-input v-model="cForm.pageSize" placeholder="20" style="width: 140px" />
          <span class="muted" style="margin-left: 8px">服务商固定条数时填 0</span>
        </el-form-item>
        <el-form-item label="支持竞品">
          <el-switch v-model="cForm.supportsCompetitor" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="cForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cVisible = false">取消</el-button>
        <el-button type="primary" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tVisible" title="测连通" width="600px">
      <p class="muted">会用第 1 页真拉一次。这次调用按条计费，只用来确认 Token 和字段对不对。</p>
      <el-form label-width="88px">
        <el-form-item label="平台">
          <el-select v-model="tForm.platform" style="width: 100%" placeholder="选择平台">
            <el-option v-for="item in testablePlatforms" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品ID">
          <el-input v-model="tForm.productId" placeholder="填一个真实商品ID，如 100012043978" />
        </el-form-item>
      </el-form>

      <div v-if="testResult" class="ds-test-result">
        <div class="ds-test-head">
          <span class="status-text" :class="testResult.ok ? 'is-ok' : 'is-bad'">
            {{ testResult.ok ? '通了' : '没通' }}
          </span>
          <span class="muted">{{ testResult.message }}</span>
        </div>
        <p class="cell-note">
          第 1 页 {{ testResult.itemCount }} 条，本次约 {{ money(testResult.costYuan) }} 元
          <span v-if="testResult.code !== null"> · 服务商 code={{ testResult.code }}</span>
        </p>
        <el-table :data="testResult.fields" size="small" empty-text="没有可显示的字段">
          <el-table-column label="字段" width="120" prop="name" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <span class="status-text" :class="row.ok ? 'is-ok' : 'is-bad'">{{ row.ok ? '取到' : '没取到' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="样例" min-width="220">
            <template #default="{ row }">
              <span class="muted">{{ row.sample || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <p v-if="!testResult.ok" class="muted">先看没取到的字段，多半是列表路径或字段映射填错了。</p>
      </div>

      <template #footer>
        <el-button @click="tVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="doTest">拉一次试试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'
import { ElMessage } from 'element-plus'
import { platformLabel } from '@/utils/labels'

const PLATFORMS = [
  { code: 'jd', name: '京东' },
  { code: 'taobao', name: '淘宝/天猫' },
  { code: 'douyin', name: '抖音' },
  { code: 'xiaohongshu', name: '小红书' },
]

const auth = useAuthStore()
const canEdit = computed(() => auth.has('admin:datasource:edit'))

const providers = ref<any[]>([])
const pVisible = ref(false)
const cVisible = ref(false)
const tVisible = ref(false)
const testing = ref(false)
const testResult = ref<any>(null)
const currentProvider = ref<any>(null)

const pForm = reactive<any>(emptyProvider())
const cForm = reactive<any>(emptyChannel())
const tForm = reactive<any>({ platform: 'jd', productId: '' })

const testablePlatforms = computed(() => {
  const list = currentProvider.value?.channels || []
  const enabled = list.filter((item: any) => item.enabled === 1).map((item: any) => item.platform)
  return PLATFORMS.filter((item) => enabled.includes(item.code))
})

function emptyProvider() {
  return {
    id: null,
    name: '',
    baseUrl: '',
    apiKey: '',
    protocol: 'HTTP_JSON',
    pricePerItem: 0.01,
    dailyBudgetYuan: 50,
    dailyItemLimit: 5000,
    maxPagesPerRun: 20,
    timeoutMs: 120000,
    maxRetry: 1,
    enabled: 1,
    remark: '',
  }
}

function emptyChannel(platform = 'jd') {
  return {
    platform,
    endpointPath: '',
    httpMethod: 'GET',
    requestTemplate: 'itemId=${productId}&page=${page}',
    itemsPath: '$.data',
    reviewMapping: JSON.stringify(
      {
        reviewId: '$.id',
        content: '$.content',
        reviewTime: '$.creationTime',
        likeCount: '$.likeCount',
        productId: '$.skuId',
        sourceUrl: '$.url',
      },
      null,
      2,
    ),
    pageSize: 20,
    supportsCompetitor: 1,
    enabled: 1,
  }
}

async function load() {
  const resp = await http.get('/admin/datasource/providers')
  providers.value = resp.data.data
}

function money(value?: number | string | null) {
  const num = Number(value || 0)
  return num.toFixed(num >= 1 ? 2 : 4).replace(/0+$/, '').replace(/\.$/, '')
}

function remainText(row: any) {
  if (!row.dailyBudgetYuan || Number(row.dailyBudgetYuan) <= 0) {
    return `今天已用 ${row.todayItemCount} 条，额度不限`
  }
  if (row.remainingBudgetYuan === null || row.remainingBudgetYuan === undefined) {
    return `今天已用 ${row.todayItemCount} 条`
  }
  return `还剩 ${money(row.remainingBudgetYuan)} 元，超了今天不再拉`
}

function channelSummary(row: any) {
  const list = (row.channels || []).filter((item: any) => item.enabled === 1)
  if (!list.length) {
    return '还没配平台，点「配平台」填接口和字段'
  }
  return list.map((item: any) => platformLabel(item.platform)).join('、') + ' 已配'
}

function openProvider(row?: any) {
  Object.assign(pForm, row ? { ...row, apiKey: '' } : emptyProvider())
  pVisible.value = true
}

async function saveProvider() {
  const payload: any = {
    name: pForm.name,
    protocol: 'HTTP_JSON',
    baseUrl: pForm.baseUrl,
    pricePerItem: pForm.pricePerItem,
    dailyBudgetYuan: pForm.dailyBudgetYuan,
    dailyItemLimit: pForm.dailyItemLimit,
    maxPagesPerRun: pForm.maxPagesPerRun,
    timeoutMs: pForm.timeoutMs,
    maxRetry: pForm.maxRetry,
    enabled: pForm.enabled,
    remark: pForm.remark,
  }
  if (pForm.apiKey) {
    payload.apiKey = pForm.apiKey
  }
  if (pForm.id) {
    await http.put(`/admin/datasource/providers/${pForm.id}`, payload)
  } else {
    await http.post('/admin/datasource/providers', payload)
  }
  pVisible.value = false
  await load()
}

async function toggleProvider(row: any, val: number) {
  try {
    await http.put(`/admin/datasource/providers/${row.id}`, {
      name: row.name,
      protocol: row.protocol,
      baseUrl: row.baseUrl,
      pricePerItem: row.pricePerItem,
      dailyBudgetYuan: row.dailyBudgetYuan,
      dailyItemLimit: row.dailyItemLimit,
      maxPagesPerRun: row.maxPagesPerRun,
      timeoutMs: row.timeoutMs,
      maxRetry: row.maxRetry,
      enabled: val,
      remark: row.remark,
    })
    row.enabled = val
    await load()
  } catch {
    await load()
  }
}

function openChannel(row: any) {
  currentProvider.value = row
  const used = new Set((row.channels || []).map((c: any) => c.platform))
  const next = PLATFORMS.find((p) => !used.has(p.code))?.code || 'jd'
  Object.assign(cForm, emptyChannel(next), { id: null })
  cVisible.value = true
}

function editChannel(ch: any) {
  Object.assign(cForm, emptyChannel(), { ...ch, reviewMapping: pretty(ch.reviewMapping) })
}

function pretty(json: string) {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

async function saveChannel() {
  try {
    await http.put(`/admin/datasource/providers/${currentProvider.value.id}/channels`, { ...cForm })
    cVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败，请检查必填项')
  }
}

function openTest(row: any) {
  currentProvider.value = row
  testResult.value = null
  tForm.productId = ''
  const first = (row.channels || []).find((item: any) => item.enabled === 1)
  if (first) {
    tForm.platform = first.platform
  }
  tVisible.value = true
}

async function doTest() {
  if (!tForm.productId) {
    return
  }
  testing.value = true
  try {
    const resp = await http.post(
      `/admin/datasource/providers/${currentProvider.value.id}/test`,
      null,
      { params: { platform: tForm.platform, productId: tForm.productId } },
    )
    testResult.value = resp.data.data
    await load()
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>
