package com.lumiinsight.modules.report;

import com.lumiinsight.modules.evidence.entity.Evidence;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.report.dto.ReportView;
import com.lumiinsight.modules.review.dto.ProjectOverview;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportComposer {

    public ReportView.Snapshot snapshotOf(ProjectOverview overview) {
        return ReportView.Snapshot.builder()
                .imported(overview.getImported())
                .counted(overview.getCounted())
                .analyzed(overview.getAnalyzed())
                .pos(overview.getPos())
                .neg(overview.getNeg())
                .neu(overview.getNeu())
                .build();
    }

    public List<ReportView.Section> compose(Project project, ProjectOverview overview, List<Evidence> evidence) {
        Set<Long> allowed = evidence.stream().map(Evidence::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Evidence> pos = bySentiment(evidence, "pos");
        List<Evidence> neg = bySentiment(evidence, "neg");
        Map<String, List<Evidence>> byAspect = new LinkedHashMap<>();
        for (Evidence row : evidence) {
            if (!StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            byAspect.computeIfAbsent(row.getAspectName(), key -> new ArrayList<>()).add(row);
        }
        List<String> aspectOrder = overview.getAspects() == null
                ? new ArrayList<>(byAspect.keySet())
                : overview.getAspects().stream().map(ProjectOverview.AspectItem::getName).toList();

        List<ReportView.Section> sections = new ArrayList<>();
        sections.add(section("summary", "执行摘要", summaryClaims(project, overview, pos, neg, aspectOrder, byAspect, allowed)));
        sections.add(section("volume", "声量", volumeClaims(overview, evidence, allowed)));
        sections.add(section("sentiment", "情感结构", sentimentClaims(overview, pos, neg, bySentiment(evidence, "neu"), allowed)));
        sections.add(section("aspects", "方面痛点与卖点", aspectClaims(aspectOrder, byAspect, allowed)));
        sections.add(section("quotes", "原声摘录", quoteClaims(pos, neg, allowed)));
        sections.add(section("advice", "改进建议", adviceClaims(aspectOrder, byAspect, pos, allowed)));
        return sections.stream().filter(s -> s.getClaims() != null && !s.getClaims().isEmpty()).toList();
    }

    private List<ReportView.Claim> summaryClaims(
            Project project,
            ProjectOverview overview,
            List<Evidence> pos,
            List<Evidence> neg,
            List<String> aspectOrder,
            Map<String, List<Evidence>> byAspect,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        String name = project.getName() == null ? "本项目" : project.getName();
        add(claims, name + " 有效评论 " + overview.getCounted() + " 条，已导入 " + overview.getImported() + " 条。", ids(first(all(pos, neg), 3), allowed));
        if (overview.getPos() >= overview.getNeg() && overview.getPos() > 0) {
            add(claims, "整体偏正向，用户认可的卖点可以继续强化。", ids(first(pos, 3), allowed));
        } else if (overview.getNeg() > 0) {
            add(claims, "负向反馈需要优先处理，不宜只看声量。", ids(first(neg, 3), allowed));
        }
        String topPos = firstAspectWith(aspectOrder, byAspect, "pos");
        if (topPos != null) {
            add(claims, "「" + topPos + "」正向提到较多，适合作为卖点表述。", ids(first(bySentiment(byAspect.get(topPos), "pos"), 3), allowed));
        }
        String topNeg = firstAspectWith(aspectOrder, byAspect, "neg");
        if (topNeg != null) {
            add(claims, "「" + topNeg + "」负向相对集中，建议在报告里单独跟进。", ids(first(bySentiment(byAspect.get(topNeg), "neg"), 3), allowed));
        }
        return claims;
    }

    private List<ReportView.Claim> volumeClaims(ProjectOverview overview, List<Evidence> evidence, Set<Long> allowed) {
        List<ReportView.Claim> claims = new ArrayList<>();
        add(claims, "有效评论 " + overview.getCounted() + " 条，已分析 " + overview.getAnalyzed() + " 条。声量只统计有效评论，不含重复、广告和过短。", ids(first(evidence, 3), allowed));
        return claims;
    }

    private List<ReportView.Claim> sentimentClaims(
            ProjectOverview overview,
            List<Evidence> pos,
            List<Evidence> neg,
            List<Evidence> neu,
            Set<Long> allowed
    ) {
        long total = Math.max(overview.getAnalyzed(), 1);
        List<ReportView.Claim> claims = new ArrayList<>();
        add(claims, "正向 " + overview.getPos() + " 条（" + pct(overview.getPos(), total) + "）。", ids(first(pos, 3), allowed));
        add(claims, "负向 " + overview.getNeg() + " 条（" + pct(overview.getNeg(), total) + "）。", ids(first(neg, 3), allowed));
        add(claims, "中性 " + overview.getNeu() + " 条（" + pct(overview.getNeu(), total) + "）。", ids(first(neu, 3), allowed));
        return claims;
    }

    private List<ReportView.Claim> aspectClaims(
            List<String> aspectOrder,
            Map<String, List<Evidence>> byAspect,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        int shown = 0;
        for (String name : aspectOrder) {
            if (shown >= 6) {
                break;
            }
            List<Evidence> rows = byAspect.getOrDefault(name, List.of());
            List<Evidence> pos = bySentiment(rows, "pos");
            List<Evidence> neg = bySentiment(rows, "neg");
            if (pos.isEmpty() && neg.isEmpty()) {
                continue;
            }
            shown++;
            if (!pos.isEmpty()) {
                add(claims, "「" + name + "」卖点：" + quoteOf(pos.get(0)) + "。", ids(first(pos, 3), allowed));
            }
            if (!neg.isEmpty()) {
                add(claims, "「" + name + "」痛点：" + quoteOf(neg.get(0)) + "。", ids(first(neg, 3), allowed));
            }
        }
        return claims;
    }

    private List<ReportView.Claim> quoteClaims(List<Evidence> pos, List<Evidence> neg, Set<Long> allowed) {
        List<Evidence> mixed = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (mixed.size() < 6 && (i < pos.size() || j < neg.size())) {
            if (j < neg.size()) {
                mixed.add(neg.get(j++));
            }
            if (mixed.size() >= 6) {
                break;
            }
            if (i < pos.size()) {
                mixed.add(pos.get(i++));
            }
        }
        List<ReportView.Claim> claims = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Evidence row : mixed) {
            if (row.getId() == null || !seen.add(row.getId())) {
                continue;
            }
            String aspect = StringUtils.hasText(row.getAspectName()) ? "「" + row.getAspectName() + "」" : "";
            add(claims, aspect + quoteOf(row), ids(List.of(row), allowed));
        }
        return claims;
    }

    private List<ReportView.Claim> adviceClaims(
            List<String> aspectOrder,
            Map<String, List<Evidence>> byAspect,
            List<Evidence> pos,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        int n = 0;
        for (String name : aspectOrder) {
            List<Evidence> neg = bySentiment(byAspect.getOrDefault(name, List.of()), "neg");
            if (neg.isEmpty()) {
                continue;
            }
            add(claims, "建议优先改进「" + name + "」，负向原评已指向具体体验问题。", ids(first(neg, 3), allowed));
            n++;
            if (n >= 3) {
                break;
            }
        }
        if (claims.isEmpty() && !pos.isEmpty()) {
            add(claims, "暂无集中负向，可继续沿用现有卖点表述，并抽查原评是否稳定。", ids(first(pos, 3), allowed));
        }
        return claims;
    }

    private static ReportView.Section section(String code, String title, List<ReportView.Claim> claims) {
        return ReportView.Section.builder().code(code).title(title).claims(claims).build();
    }

    private static void add(List<ReportView.Claim> claims, String text, List<Long> evidenceIds) {
        if (!StringUtils.hasText(text) || evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        claims.add(ReportView.Claim.builder().text(text.trim()).evidenceIds(evidenceIds).build());
    }

    private static List<Long> ids(List<Evidence> rows, Set<Long> allowed) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(Evidence::getId)
                .filter(id -> id != null && allowed.contains(id))
                .distinct()
                .limit(3)
                .toList();
    }

    private static List<Evidence> first(List<Evidence> rows, int n) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().limit(n).toList();
    }

    private static List<Evidence> all(List<Evidence> a, List<Evidence> b) {
        List<Evidence> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out;
    }

    private static List<Evidence> bySentiment(List<Evidence> rows, String sentiment) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(row -> sentiment.equals(row.getSentiment())).toList();
    }

    private static String firstAspectWith(List<String> order, Map<String, List<Evidence>> byAspect, String sentiment) {
        for (String name : order) {
            if (!bySentiment(byAspect.getOrDefault(name, List.of()), sentiment).isEmpty()) {
                return name;
            }
        }
        return byAspect.entrySet().stream()
                .filter(e -> !bySentiment(e.getValue(), sentiment).isEmpty())
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static String quoteOf(Evidence row) {
        if (StringUtils.hasText(row.getQuote())) {
            return row.getQuote().trim();
        }
        return "见对应原评";
    }

    private static String pct(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(part * 100.0 / total) + "%";
    }
}
