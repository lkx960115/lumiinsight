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
      <p class="muted">侧栏「项目」。已有「演示吸顶灯」可直接点进去；没有就点「新建项目」，主型号填「演示吸顶灯」，竞品填「星辉竞品灯」。</p>
      <el-button @click="router.push('/app/projects')">去项目列表</el-button>
    </el-card>

    <el-card class="block">
      <template #header>3. 导入样例评论</template>
      <p class="muted">点「下载样例」一次导入即可，里面已有本型号和竞品原评。也可从京东/淘宝/抖店/小红书商家后台导出评价表再导入。系统认「评价内容」「评论内容」等列名。点「选择文件导入」，等到导入完成。不要在这里抓取平台。</p>
    </el-card>

    <el-card class="block">
      <template #header>4. 清洗并分析</template>
      <p class="muted">点右上角「清洗并分析」。清洗、分析、建检索、出报告都能看到完成多少，可以离开页面再回来。失败了在分析记录里点「重试」。若提示连不上分析服务，先在本机启动分析进程。</p>
    </el-card>

    <el-card class="block">
      <template #header>5. 看图表和原评</template>
      <p class="muted">概览里看情感、方面和按月走势。点方面条会筛出对应原评。某方面负向突然变多、新槽点出现、或有效评论明显变少时，项目详情「口碑预警」会提示，并可查看原评。评论列表可改成「按意思找」。展开评论行可看方面、情感和原因。</p>
    </el-card>

    <el-card class="block">
      <template #header>6. 打开报告并导出</template>
      <p class="muted">分析完成后点「打开报告」，或走侧栏「报告」。摘要和建议会带原评；配了摘要模型时会改写成顾问口吻，失败仍用规则稿。样例里已有竞品「星辉竞品灯」，对照可并排看声量、方面和按月走势。报告里还有「问原评」一节，按现成问法对照原评写成，没有依据的问题不出现。每条结论点「查看原评」。项目详情和报告页右侧可点「问原评」，只根据本项目原评回答，关掉后再打开还能看到问过的。需要带走时导出 PDF、表格或文稿，文末有引用编号。</p>
      <el-button @click="router.push('/app/reports')">去报告列表</el-button>
    </el-card>

    <el-card class="block">
      <template #header>若出错</template>
      <p class="muted">导入失败可下载失败行。分析失败看任务说明后重试。报告没有证据时，请先完成分析再生成。导出失败时重启 Java 后再试。PDF 需要本机有中文字体。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
