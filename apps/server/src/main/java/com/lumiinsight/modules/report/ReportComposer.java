package com.lumiinsight.modules.report;

import com.lumiinsight.modules.evidence.entity.Evidence;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.report.dto.ReportView;
import com.lumiinsight.modules.review.dto.ProjectOverview;
import com.lumiinsight.modules.review.entity.Review;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;

    public ReportComposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
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

    public List<ReportView.Section> compose(Project project, ProjectOverview overview, List<Evidence> evidence, List<Review> countedReviews) {
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
        sections.add(section("compete", "竞品对照", competeClaims(project, evidence, countedReviews, allowed)));
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

    private List<ReportView.Claim> competeClaims(
            Project project,
            List<Evidence> evidence,
            List<Review> countedReviews,
            Set<Long> allowed
    ) {
        List<String> rivals = readNames(project.getCompetitors());
        Map<Long, Review> byReview = new HashMap<>();
        if (countedReviews != null) {
            for (Review row : countedReviews) {
                if (row.getId() != null) {
                    byReview.put(row.getId(), row);
                }
            }
        }
        String ownKey = StringUtils.hasText(project.getMainModel()) ? project.getMainModel() : project.getBrand();
        List<Evidence> own = new ArrayList<>();
        List<Evidence> rivalEv = new ArrayList<>();
        for (Evidence row : evidence) {
            Review review = byReview.get(row.getReviewId());
            String product = review == null ? "" : String.valueOf(review.getProductName() == null ? "" : review.getProductName());
            if (matchesAny(product, rivals)) {
                rivalEv.add(row);
            } else if (!StringUtils.hasText(ownKey) || containsIgnore(product, ownKey) || !StringUtils.hasText(product)) {
                own.add(row);
            }
        }
        List<ReportView.Claim> claims = new ArrayList<>();
        if (rivals.isEmpty()) {
            add(claims, "项目还没填写竞品。在项目里补上竞品名称，并导入带对应商品名的评论后，这里会对照声量和痛点。", ids(first(evidence, 3), allowed));
            return claims;
        }
        if (rivalEv.isEmpty()) {
            add(claims, "已填写竞品「" + String.join("、", rivals) + "」，但评论商品名里还没有对应条目。请导入竞品评论后再生成。", ids(first(own.isEmpty() ? evidence : own, 3), allowed));
            return claims;
        }
        add(claims, "本型号相关原评 " + own.size() + " 条，竞品相关 " + rivalEv.size() + " 条（按商品名归类）。", ids(first(all(own, rivalEv), 3), allowed));
        List<Evidence> ownNeg = bySentiment(own, "neg");
        List<Evidence> rivalNeg = bySentiment(rivalEv, "neg");
        if (!rivalNeg.isEmpty()) {
            add(claims, "竞品负向更值得盯：「" + (StringUtils.hasText(rivalNeg.get(0).getAspectName()) ? rivalNeg.get(0).getAspectName() : "体验") + "」" + quoteOf(rivalNeg.get(0)) + "。", ids(first(rivalNeg, 3), allowed));
        }
        if (!ownNeg.isEmpty() && !rivalNeg.isEmpty()) {
            add(claims, "本型号也有负向，改品时不要只看竞品槽点，两边原评都要核对。", ids(first(all(ownNeg, rivalNeg), 3), allowed));
        } else if (!ownNeg.isEmpty()) {
            add(claims, "竞品原评里负向较少，本型号仍有可改进点，建议对照原评改描述或参数。", ids(first(ownNeg, 3), allowed));
        }
        return claims;
    }

    private List<String> readNames(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<>() {
            });
            return names == null ? List.of() : names.stream().map(String::trim).filter(StringUtils::hasText).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean matchesAny(String product, List<String> names) {
        for (String name : names) {
            if (containsIgnore(product, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnore(String haystack, String needle) {
        if (!StringUtils.hasText(haystack) || !StringUtils.hasText(needle)) {
            return false;
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
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
