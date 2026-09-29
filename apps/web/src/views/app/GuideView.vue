<template>
  <div>
    <div class="page-head">
      <div>
        <h2>怎么用</h2>
        <p class="muted">大约 15 分钟走完一次演示：导入样例 → 清洗并分析 → 看报告和原评 → 导出。</p>
      </div>
    </div>

    <el-card class="block">
      <template #header>1. 登录</template>
      <p class="muted">用启动日志里打印过一次的管理员账号。分析员账号只能进工作台，看不到系统管理。</p>
    </el-card>

    <el-card class="block">
      <template #header>2. 打开或新建项目</template>
      <p class="muted">侧栏「项目」。已有「演示吸顶灯」可直接点进去；没有就点「新建项目」，填品牌和主型号即可。</p>
      <el-button @click="router.push('/app/projects')">去项目列表</el-button>
    </el-card>

    <el-card class="block">
      <template #header>3. 导入样例评论</template>
      <p class="muted">用仓库里的 <code>eval/fixtures/sample-reviews.csv</code>。点「选择文件导入」，等到导入完成（大约几十秒）。不要在这里抓取平台。</p>
    </el-card>

    <el-card class="block">
      <template #header>4. 清洗并分析</template>
      <p class="muted">点右上角「清洗并分析」，等状态变成完成。通常要一两分钟，请不要关掉页面。失败了在分析记录里点「重试」。若提示连不上分析服务，先在本机启动 Worker。</p>
    </el-card>

    <el-card class="block">
      <template #header>5. 看图表和原评</template>
      <p class="muted">概览里看情感和方面。点方面条会筛出对应原评。展开评论行可看方面、情感和原因。</p>
    </el-card>

    <el-card class="block">
      <template #header>6. 打开报告并导出</template>
      <p class="muted">分析完成后点「打开报告」，或走侧栏「报告」。每条结论点「查看原评」。再点「导出」下载表格或文稿，文末有引用编号。</p>
      <el-button @click="router.push('/app/reports')">去报告列表</el-button>
    </el-card>

    <el-card class="block">
      <template #header>若出错</template>
      <p class="muted">导入失败可下载失败行。分析失败看任务说明后重试。报告没有证据时，请先完成分析再生成。导出表格失败时重启 Java 后再试。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
