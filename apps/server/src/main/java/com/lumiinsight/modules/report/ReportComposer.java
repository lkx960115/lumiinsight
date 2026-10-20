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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
        Map<Long, Review> byReview = indexReviews(countedReviews);
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
        sections.add(section("summary", "执行摘要", summaryClaims(project, overview, pos, neg, aspectOrder, byAspect, byReview, allowed)));
        sections.add(section("volume", "声量", volumeClaims(overview, evidence, allowed)));
        sections.add(section("sentiment", "情感结构", sentimentClaims(overview, pos, neg, bySentiment(evidence, "neu"), allowed)));
        sections.add(section("aspects", "方面痛点与卖点", aspectClaims(aspectOrder, byAspect, byReview, allowed)));
        sections.add(section("quotes", "原声摘录", quoteClaims(pos, neg, allowed)));
        sections.add(competeSection(project, evidence, byReview, allowed));
        sections.add(section("advice", "改进建议", adviceClaims(aspectOrder, byAspect, pos, byReview, allowed)));
        sections.add(section("ask", "问原评", askClaims(project, byAspect, evidence, byReview, allowed)));
        return sections.stream().filter(s -> s.getClaims() != null && !s.getClaims().isEmpty()).toList();
    }

    private List<ReportView.Claim> summaryClaims(
            Project project,
            ProjectOverview overview,
            List<Evidence> pos,
            List<Evidence> neg,
            List<String> aspectOrder,
            Map<String, List<Evidence>> byAspect,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        String name = project.getName() == null ? "本项目" : project.getName();
        add(claims, name + " 有效评论 " + overview.getCounted() + " 条（正向 " + overview.getPos()
                + "、负向 " + overview.getNeg() + "、中性 " + overview.getNeu() + "）。", ids(first(all(pos, neg), 3), allowed));
        String topNeg = densestAspect(aspectOrder, byAspect, "neg");
        if (topNeg != null) {
            List<Evidence> rows = bySentiment(byAspect.get(topNeg), "neg");
            add(claims, "「" + topNeg + "」负向最集中，例如「" + quoteOf(rows.get(0)) + "」。", ids(first(rows, 3), allowed));
        }
        String topPos = densestAspect(aspectOrder, byAspect, "pos");
        if (topPos != null) {
            List<Evidence> rows = bySentiment(byAspect.get(topPos), "pos");
            add(claims, "「" + topPos + "」被提到较多的卖点，例如「" + quoteOf(rows.get(0)) + "」。", ids(first(rows, 3), allowed));
        }
        addMonthTrend(claims, overview, neg, byReview, allowed);
        return claims;
    }

    private void addMonthTrend(
            List<ReportView.Claim> claims,
            ProjectOverview overview,
            List<Evidence> neg,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<ProjectOverview.MonthItem> months = overview.getMonths();
        if (months == null || months.size() < 2) {
            return;
        }
        ProjectOverview.MonthItem first = months.get(0);
        ProjectOverview.MonthItem last = months.get(months.size() - 1);
        long firstTotal = first.getPos() + first.getNeg() + first.getNeu();
        long lastTotal = last.getPos() + last.getNeg() + last.getNeu();
        if (firstTotal <= 0 || lastTotal <= 0) {
            return;
        }
        long firstPct = Math.round(first.getNeg() * 100.0 / firstTotal);
        long lastPct = Math.round(last.getNeg() * 100.0 / lastTotal);
        if (firstPct == lastPct) {
            return;
        }
        String dir = lastPct > firstPct ? "升到" : "降到";
        List<Evidence> recent = inMonth(neg, byReview, last.getLabel());
        if (recent.isEmpty()) {
            recent = inMonth(neg, byReview, first.getLabel());
        }
        add(claims, monthLabel(first.getLabel()) + "负向占比 " + firstPct + "%，"
                        + monthLabel(last.getLabel()) + dir + " " + lastPct + "%。改品时要看近月原评，不要只看累计。",
                ids(first(recent, 3), allowed));
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
            Map<Long, Review> byReview,
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
                add(claims, "「" + name + "」卖点：「" + quoteOf(pos.get(0)) + "」。", ids(first(pos, 3), allowed));
            }
            if (!neg.isEmpty()) {
                add(claims, "「" + name + "」痛点：「" + quoteOf(neg.get(0)) + "」。", ids(first(neg, 3), allowed));
            }
            addAspectTrend(claims, name, rows, byReview, allowed);
        }
        return claims;
    }

    private void addAspectTrend(
            List<ReportView.Claim> claims,
            String aspect,
            List<Evidence> rows,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        TreeMap<String, long[]> byMonth = new TreeMap<>();
        Map<String, List<Evidence>> negByMonth = new HashMap<>();
        for (Evidence row : rows) {
            String key = monthKey(byReview.get(row.getReviewId()));
            if (key == null) {
                continue;
            }
            long[] bucket = byMonth.computeIfAbsent(key, ignored -> new long[2]);
            bucket[1]++;
            if ("neg".equals(row.getSentiment())) {
                bucket[0]++;
                negByMonth.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        }
        if (byMonth.size() < 2) {
            return;
        }
        String first = byMonth.firstKey();
        String last = byMonth.lastKey();
        long[] a = byMonth.get(first);
        long[] b = byMonth.get(last);
        if (a[1] < 2 || b[1] < 2) {
            return;
        }
        long firstPct = Math.round(a[0] * 100.0 / a[1]);
        long lastPct = Math.round(b[0] * 100.0 / b[1]);
        if (firstPct == lastPct) {
            return;
        }
        boolean worse = lastPct > firstPct;
        String text = "「" + aspect + "」负向占比从 " + monthLabel(first) + " 的 " + firstPct + "% "
                + (worse ? "升到" : "降到") + " " + monthLabel(last) + " 的 " + lastPct + "%，"
                + (worse ? "在变差。" : "有好转。");
        List<Evidence> proof = negByMonth.getOrDefault(worse ? last : first, List.of());
        if (proof.isEmpty()) {
            proof = rows;
        }
        add(claims, text, ids(first(proof, 3), allowed));
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
            add(claims, aspect + "「" + quoteOf(row) + "」", ids(List.of(row), allowed));
        }
        return claims;
    }

    private ReportView.Section competeSection(
            Project project,
            List<Evidence> evidence,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<String> rivals = readNames(project.getCompetitors());
        String ownKey = StringUtils.hasText(project.getMainModel()) ? project.getMainModel() : project.getBrand();
        List<Evidence> own = new ArrayList<>();
        Map<String, List<Evidence>> byRival = new LinkedHashMap<>();
        for (String rival : rivals) {
            byRival.put(rival, new ArrayList<>());
        }
        for (Evidence row : evidence) {
            Review review = byReview.get(row.getReviewId());
            String product = review == null ? "" : String.valueOf(review.getProductName() == null ? "" : review.getProductName());
            String matched = firstMatch(product, rivals);
            if (matched != null) {
                byRival.get(matched).add(row);
            } else if (!StringUtils.hasText(ownKey) || containsIgnore(product, ownKey) || !StringUtils.hasText(product)) {
                own.add(row);
            }
        }
        List<String> present = rivals.stream().filter(name -> !byRival.get(name).isEmpty()).toList();
        List<String> missing = rivals.stream().filter(name -> byRival.get(name).isEmpty()).toList();
        List<ReportView.Claim> claims = new ArrayList<>();
        if (rivals.isEmpty()) {
            add(claims, "项目还没填写竞品。在项目详情补上竞品名称，并导入带对应商品名的评论后，这里会对照声量和方面。", ids(first(evidence, 3), allowed));
            return section("compete", "竞品对照", claims);
        }
        if (present.isEmpty()) {
            add(claims, "已填写竞品「" + String.join("、", rivals) + "」，但评论商品名里还没有对应条目。请导入竞品评论后再生成。", ids(first(own.isEmpty() ? evidence : own, 3), allowed));
            return section("compete", "竞品对照", claims);
        }
        String ownName = StringUtils.hasText(ownKey) ? ownKey : "本型号";
        Counts ownCounts = countsOf(own, byReview);
        List<ReportView.CompareRow> compare = new ArrayList<>();
        Map<String, List<Evidence>> byParty = new LinkedHashMap<>();
        if (ownCounts.volume > 0) {
            compare.add(compareRow(ownName, ownCounts, own));
            byParty.put(ownName, own);
        }
        for (String rival : present) {
            List<Evidence> rows = byRival.get(rival);
            compare.add(compareRow(rival, countsOf(rows, byReview), rows));
            byParty.put(rival, rows);
        }
        List<ReportView.AspectCompareRow> aspectCompare = aspectCompareOf(byParty);
        List<ReportView.MonthCompareRow> monthCompare = monthCompareOf(byParty, byReview);
        add(claims, ownName + " 原评 " + ownCounts.volume + " 条（正向 " + ownCounts.pos + "、负向 " + ownCounts.neg
                        + "），已对照 " + present.size() + " 个竞品：" + String.join("、", present) + "。",
                ids(first(all(own, byRival.get(present.get(0))), 3), allowed));
        addPainClaim(claims, ownName, own, allowed);
        addAspectCompareClaims(claims, ownName, aspectCompare, byParty, allowed);
        addCompeteTrendClaims(claims, ownName, byParty, byReview, allowed);
        if (!missing.isEmpty()) {
            add(claims, "已填写竞品「" + String.join("、", missing) + "」，但评论商品名里还没有对应条目。请导入竞品评论后再生成。",
                    ids(first(own.isEmpty() ? evidence : own, 3), allowed));
        }
        return section("compete", "竞品对照", claims, compare, aspectCompare, monthCompare);
    }

    private static void addPainClaim(List<ReportView.Claim> claims, String name, List<Evidence> rows, Set<Long> allowed) {
        String pain = topPainName(rows);
        if (pain == null) {
            return;
        }
        List<Evidence> neg = bySentiment(byAspect(rows, pain), "neg");
        add(claims, name + " 最集中痛点「" + pain + "」：「" + quoteOf(neg.get(0)) + "」。", ids(first(neg, 3), allowed));
    }

    private static List<ReportView.AspectCompareRow> aspectCompareOf(Map<String, List<Evidence>> byParty) {
        Map<String, Long> weight = new LinkedHashMap<>();
        for (List<Evidence> rows : byParty.values()) {
            for (Evidence row : rows) {
                if (!StringUtils.hasText(row.getAspectName())) {
                    continue;
                }
                long add = "neg".equals(row.getSentiment()) ? 2L : 1L;
                weight.merge(row.getAspectName(), add, Long::sum);
            }
        }
        List<String> aspects = weight.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(8)
                .toList();
        List<ReportView.AspectCompareRow> out = new ArrayList<>();
        for (String aspect : aspects) {
            List<ReportView.AspectScore> scores = new ArrayList<>();
            for (Map.Entry<String, List<Evidence>> party : byParty.entrySet()) {
                List<Evidence> rows = byAspect(party.getValue(), aspect);
                scores.add(ReportView.AspectScore.builder()
                        .name(party.getKey())
                        .pos(bySentiment(rows, "pos").size())
                        .neg(bySentiment(rows, "neg").size())
                        .build());
            }
            out.add(ReportView.AspectCompareRow.builder().aspect(aspect).scores(scores).build());
        }
        return out;
    }

    private static void addAspectCompareClaims(
            List<ReportView.Claim> claims,
            String ownName,
            List<ReportView.AspectCompareRow> aspectCompare,
            Map<String, List<Evidence>> byParty,
            Set<Long> allowed
    ) {
        int n = 0;
        for (ReportView.AspectCompareRow row : aspectCompare) {
            if (n >= 5) {
                break;
            }
            if (row.getScores() == null || row.getScores().isEmpty()) {
                continue;
            }
            ReportView.AspectScore worst = null;
            ReportView.AspectScore best = null;
            for (ReportView.AspectScore score : row.getScores()) {
                if (worst == null || score.getNeg() > worst.getNeg()) {
                    worst = score;
                }
                if (best == null || score.getNeg() < best.getNeg()) {
                    best = score;
                }
            }
            if (worst == null || worst.getNeg() <= 0 || best == null || worst.getNeg() <= best.getNeg()) {
                continue;
            }
            List<Evidence> source = bySentiment(byAspect(byParty.get(worst.getName()), row.getAspect()), "neg");
            if (source.isEmpty()) {
                continue;
            }
            StringBuilder bits = new StringBuilder();
            for (ReportView.AspectScore score : row.getScores()) {
                if (bits.length() > 0) {
                    bits.append("，");
                }
                bits.append(score.getName()).append("负向 ").append(score.getNeg()).append(" 条");
            }
            String quote = "「" + quoteOf(source.get(0)) + "」";
            if (ownName.equals(worst.getName())) {
                add(claims, "「" + row.getAspect() + "」" + bits + "。本型号更集中：" + quote + "。",
                        ids(first(source, 3), allowed));
            } else {
                add(claims, "「" + row.getAspect() + "」" + bits + "。「" + worst.getName() + "」更集中，可作对照卖点：" + quote + "。",
                        ids(first(source, 3), allowed));
            }
            n++;
        }
    }

    private static List<ReportView.MonthCompareRow> monthCompareOf(
            Map<String, List<Evidence>> byParty,
            Map<Long, Review> byReview
    ) {
        TreeSet<String> months = new TreeSet<>();
        Map<String, Map<String, Counts>> grid = new LinkedHashMap<>();
        for (Map.Entry<String, List<Evidence>> party : byParty.entrySet()) {
            Map<String, List<Evidence>> byMonth = new TreeMap<>();
            for (Evidence row : party.getValue()) {
                String key = monthKey(byReview.get(row.getReviewId()));
                if (key == null) {
                    continue;
                }
                months.add(key);
                byMonth.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
            Map<String, Counts> counts = new LinkedHashMap<>();
            for (Map.Entry<String, List<Evidence>> month : byMonth.entrySet()) {
                counts.put(month.getKey(), countsOf(month.getValue(), byReview));
            }
            grid.put(party.getKey(), counts);
        }
        if (months.isEmpty()) {
            return List.of();
        }
        List<ReportView.MonthCompareRow> out = new ArrayList<>();
        for (String month : months) {
            List<ReportView.MonthScore> scores = new ArrayList<>();
            for (String name : byParty.keySet()) {
                Counts counts = grid.getOrDefault(name, Map.of()).get(month);
                if (counts == null) {
                    counts = new Counts(0, 0, 0, 0);
                }
                scores.add(ReportView.MonthScore.builder()
                        .name(name)
                        .volume(counts.volume)
                        .pos(counts.pos)
                        .neg(counts.neg)
                        .build());
            }
            out.add(ReportView.MonthCompareRow.builder().month(month).scores(scores).build());
        }
        return out;
    }

    private void addCompeteTrendClaims(
            List<ReportView.Claim> claims,
            String ownName,
            Map<String, List<Evidence>> byParty,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        int n = 0;
        for (Map.Entry<String, List<Evidence>> party : byParty.entrySet()) {
            if (n >= 4) {
                break;
            }
            TreeMap<String, Counts> months = monthsOf(party.getValue(), byReview);
            if (months.size() < 2) {
                continue;
            }
            String first = months.firstKey();
            String last = months.lastKey();
            Counts earlier = months.get(first);
            Counts later = months.get(last);
            if (earlier.volume < 2 || later.volume < 2) {
                continue;
            }
            long firstPct = Math.round(earlier.neg * 100.0 / earlier.volume);
            long lastPct = Math.round(later.neg * 100.0 / later.volume);
            if (firstPct == lastPct) {
                continue;
            }
            boolean worse = lastPct > firstPct;
            List<Evidence> proof = inMonth(bySentiment(party.getValue(), "neg"), byReview, worse ? last : first);
            if (proof.isEmpty()) {
                proof = inMonth(party.getValue(), byReview, last);
            }
            if (proof.isEmpty()) {
                continue;
            }
            add(claims, party.getKey() + " 负向占比从 " + monthLabel(first) + " 的 " + firstPct + "% "
                            + (worse ? "升到" : "降到") + " " + monthLabel(last) + " 的 " + lastPct + "%，"
                            + (worse ? "在变差。" : "有好转。")
                            + "「" + quoteOf(proof.get(0)) + "」。",
                    ids(first(proof, 3), allowed));
            n++;
        }
        addLatestMonthWhoWorse(claims, ownName, byParty, byReview, allowed);
    }

    private void addLatestMonthWhoWorse(
            List<ReportView.Claim> claims,
            String ownName,
            Map<String, List<Evidence>> byParty,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        Map<String, TreeMap<String, Counts>> grid = new LinkedHashMap<>();
        TreeSet<String> months = new TreeSet<>();
        for (Map.Entry<String, List<Evidence>> party : byParty.entrySet()) {
            TreeMap<String, Counts> byMonth = monthsOf(party.getValue(), byReview);
            grid.put(party.getKey(), byMonth);
            months.addAll(byMonth.keySet());
        }
        String picked = null;
        for (String month : months.descendingSet()) {
            int ready = 0;
            for (TreeMap<String, Counts> byMonth : grid.values()) {
                Counts counts = byMonth.get(month);
                if (counts != null && counts.volume >= 2) {
                    ready++;
                }
            }
            if (ready >= 2) {
                picked = month;
                break;
            }
        }
        if (picked == null) {
            return;
        }
        String worst = null;
        String best = null;
        long worstPct = -1;
        long bestPct = 101;
        Counts worstCounts = null;
        for (Map.Entry<String, TreeMap<String, Counts>> party : grid.entrySet()) {
            Counts counts = party.getValue().get(picked);
            if (counts == null || counts.volume < 2) {
                continue;
            }
            long pct = Math.round(counts.neg * 100.0 / counts.volume);
            if (pct > worstPct) {
                worstPct = pct;
                worst = party.getKey();
                worstCounts = counts;
            }
            if (pct < bestPct) {
                bestPct = pct;
                best = party.getKey();
            }
        }
        if (worst == null || best == null || worst.equals(best) || worstPct <= bestPct || worstCounts == null) {
            return;
        }
        List<Evidence> proof = inMonth(bySentiment(byParty.get(worst), "neg"), byReview, picked);
        if (proof.isEmpty()) {
            return;
        }
        String how = ownName.equals(worst)
                ? "本型号近月负向更集中，要先看原评。"
                : "「" + worst + "」近月负向更集中，可作对照卖点。";
        add(claims, monthLabel(picked) + "「" + worst + "」负向占比 " + worstPct + "%，「"
                        + best + "」" + bestPct + "%。" + how + "「" + quoteOf(proof.get(0)) + "」。",
                ids(first(proof, 3), allowed));
    }

    private static TreeMap<String, Counts> monthsOf(List<Evidence> rows, Map<Long, Review> byReview) {
        Map<String, List<Evidence>> byMonth = new TreeMap<>();
        for (Evidence row : rows) {
            String key = monthKey(byReview.get(row.getReviewId()));
            if (key == null) {
                continue;
            }
            byMonth.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        TreeMap<String, Counts> out = new TreeMap<>();
        for (Map.Entry<String, List<Evidence>> month : byMonth.entrySet()) {
            out.put(month.getKey(), countsOf(month.getValue(), byReview));
        }
        return out;
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

    private static String firstMatch(String product, List<String> names) {
        for (String name : names) {
            if (containsIgnore(product, name)) {
                return name;
            }
        }
        return null;
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
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        int n = 0;
        for (String name : aspectOrder) {
            List<Evidence> rows = byAspect.getOrDefault(name, List.of());
            List<Evidence> neg = bySentiment(rows, "neg");
            if (neg.isEmpty()) {
                continue;
            }
            add(claims, "优先改「" + name + "」：原评说「" + quoteOf(neg.get(0)) + "」。", ids(first(neg, 3), allowed));
            int before = claims.size();
            addAspectTrend(claims, name, rows, byReview, allowed);
            if (claims.size() > before) {
                ReportView.Claim trend = claims.remove(claims.size() - 1);
                String extra = trend.getText().contains("变差")
                        ? "「" + name + "」近月负向在增加，不要只当个例。"
                        : "「" + name + "」近月负向在减少，仍建议抽查原评是否稳定。";
                add(claims, extra, trend.getEvidenceIds());
            }
            n++;
            if (n >= 3) {
                break;
            }
        }
        if (claims.isEmpty() && !pos.isEmpty()) {
            add(claims, "暂无集中负向，可继续沿用现有卖点表述，并抽查原评是否稳定。例如「" + quoteOf(pos.get(0)) + "」。", ids(first(pos, 3), allowed));
        }
        return claims;
    }

    private List<ReportView.Claim> askClaims(
            Project project,
            Map<String, List<Evidence>> byAspect,
            List<Evidence> evidence,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<ReportView.Claim> claims = new ArrayList<>();
        addAskNeg(claims, "安装最常被吐槽的是什么？", "安装", byAspect, allowed, 2);
        addAskColor(claims, byAspect, byReview, allowed);
        addAskPolarity(claims, "亮度够不够，原评怎么说？", "亮度", "有人说够亮", "有人嫌暗", byAspect, allowed);
        addAskPolarity(claims, "外观好不好看，原评怎么说？", "外观", "有人说好看", "有人嫌不好看", byAspect, allowed);
        addAskNeg(claims, "售后负向主要集中在哪？", "售后", byAspect, allowed, 1);
        addAskPolarity(claims, "价格贵不贵，原评怎么说？", "价格", "有人说实惠", "有人嫌贵", byAspect, allowed);
        addAskShip(claims, byAspect, allowed);
        addAskPolarity(claims, "有没有频闪？", "频闪", "有人说没有明显频闪", "有人说有频闪", byAspect, allowed);
        addAskRival(claims, project, evidence, byReview, allowed);
        return claims;
    }

    private static void addAskNeg(
            List<ReportView.Claim> claims,
            String question,
            String aspect,
            Map<String, List<Evidence>> byAspect,
            Set<Long> allowed,
            int minNeg
    ) {
        List<Evidence> neg = bySentiment(byAspect.getOrDefault(aspect, List.of()), "neg");
        if (neg.size() < minNeg) {
            return;
        }
        add(claims, "问：" + question + " 答：「" + aspect + "」负向 " + neg.size()
                + " 条，原评写到「" + quoteOf(neg.get(0)) + "」。", ids(first(neg, 3), allowed));
    }

    private static void addAskColor(
            List<ReportView.Claim> claims,
            Map<String, List<Evidence>> byAspect,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<Evidence> rows = byAspect.getOrDefault("色温", List.of());
        List<Evidence> cold = mentions(rows, byReview, "冷");
        List<Evidence> warm = mentions(rows, byReview, "暖");
        if (cold.isEmpty() && warm.isEmpty()) {
            return;
        }
        String answer;
        List<Evidence> proof;
        if (cold.size() > warm.size()) {
            answer = "原评更多写到偏冷，例如「" + quoteOf(cold.get(0)) + "」。";
            proof = cold;
        } else if (warm.size() > cold.size()) {
            answer = "原评更多写到偏暖，例如「" + quoteOf(warm.get(0)) + "」。";
            proof = warm;
        } else {
            answer = "偏冷、偏暖都有人提。偏冷如「" + quoteOf(cold.get(0)) + "」。";
            proof = all(cold, warm);
        }
        add(claims, "问：色温偏冷还是偏暖？ 答：" + answer, ids(first(proof, 3), allowed));
    }

    private static void addAskPolarity(
            List<ReportView.Claim> claims,
            String question,
            String aspect,
            String posSaid,
            String negSaid,
            Map<String, List<Evidence>> byAspect,
            Set<Long> allowed
    ) {
        List<Evidence> rows = byAspect.getOrDefault(aspect, List.of());
        List<Evidence> neg = bySentiment(rows, "neg");
        List<Evidence> pos = bySentiment(rows, "pos");
        if (neg.isEmpty() && pos.isEmpty()) {
            return;
        }
        String answer;
        List<Evidence> proof;
        if (!neg.isEmpty() && pos.isEmpty()) {
            answer = negSaid + "，原评写到「" + quoteOf(neg.get(0)) + "」。";
            proof = neg;
        } else if (neg.isEmpty()) {
            answer = posSaid + "，原评写到「" + quoteOf(pos.get(0)) + "」。";
            proof = pos;
        } else {
            answer = negSaid + "，也" + posSaid + "。负向如「" + quoteOf(neg.get(0)) + "」。";
            proof = all(neg, pos);
        }
        add(claims, "问：" + question + " 答：" + answer, ids(first(proof, 3), allowed));
    }

    private static void addAskShip(List<ReportView.Claim> claims, Map<String, List<Evidence>> byAspect, Set<Long> allowed) {
        List<Evidence> logistics = bySentiment(byAspect.getOrDefault("物流", List.of()), "neg");
        List<Evidence> pack = bySentiment(byAspect.getOrDefault("包装", List.of()), "neg");
        List<Evidence> neg = all(logistics, pack);
        if (neg.isEmpty()) {
            return;
        }
        String which;
        if (!logistics.isEmpty() && !pack.isEmpty()) {
            which = "物流和包装都有负向";
        } else if (!logistics.isEmpty()) {
            which = "物流有负向";
        } else {
            which = "包装有负向";
        }
        add(claims, "问：物流和包装有没有问题？ 答：" + which + "，原评写到「" + quoteOf(neg.get(0)) + "」。",
                ids(first(neg, 3), allowed));
    }

    private void addAskRival(
            List<ReportView.Claim> claims,
            Project project,
            List<Evidence> evidence,
            Map<Long, Review> byReview,
            Set<Long> allowed
    ) {
        List<String> rivals = readNames(project.getCompetitors());
        if (rivals.isEmpty()) {
            return;
        }
        List<Evidence> rivalRows = new ArrayList<>();
        for (Evidence row : evidence) {
            Review review = byReview.get(row.getReviewId());
            String product = review == null ? "" : String.valueOf(review.getProductName() == null ? "" : review.getProductName());
            if (firstMatch(product, rivals) != null) {
                rivalRows.add(row);
            }
        }
        Map<String, List<Evidence>> byAspect = new LinkedHashMap<>();
        for (Evidence row : rivalRows) {
            if (StringUtils.hasText(row.getAspectName())) {
                byAspect.computeIfAbsent(row.getAspectName(), key -> new ArrayList<>()).add(row);
            }
        }
        String pain = densestAspect(null, byAspect, "neg");
        if (pain == null) {
            return;
        }
        List<Evidence> neg = bySentiment(byAspect.get(pain), "neg");
        if (neg.isEmpty()) {
            return;
        }
        add(claims, "问：竞品被抱怨最多的是哪方面？ 答：竞品「" + pain + "」负向最集中，原评写到「"
                + quoteOf(neg.get(0)) + "」。", ids(first(neg, 3), allowed));
    }

    private static List<Evidence> mentions(List<Evidence> rows, Map<Long, Review> byReview, String word) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Evidence> out = new ArrayList<>();
        for (Evidence row : rows) {
            StringBuilder text = new StringBuilder();
            if (row.getQuote() != null) {
                text.append(row.getQuote());
            }
            Review review = byReview == null ? null : byReview.get(row.getReviewId());
            if (review != null && review.getContent() != null) {
                text.append(review.getContent());
            }
            if (text.toString().contains(word)) {
                out.add(row);
            }
        }
        return out;
    }

    private static ReportView.Section section(String code, String title, List<ReportView.Claim> claims) {
        return section(code, title, claims, null);
    }

    private static ReportView.Section section(String code, String title, List<ReportView.Claim> claims, List<ReportView.CompareRow> compare) {
        return section(code, title, claims, compare, null);
    }

    private static ReportView.Section section(
            String code,
            String title,
            List<ReportView.Claim> claims,
            List<ReportView.CompareRow> compare,
            List<ReportView.AspectCompareRow> aspectCompare
    ) {
        return section(code, title, claims, compare, aspectCompare, null);
    }

    private static ReportView.Section section(
            String code,
            String title,
            List<ReportView.Claim> claims,
            List<ReportView.CompareRow> compare,
            List<ReportView.AspectCompareRow> aspectCompare,
            List<ReportView.MonthCompareRow> monthCompare
    ) {
        return ReportView.Section.builder()
                .code(code)
                .title(title)
                .claims(claims)
                .compare(compare)
                .aspectCompare(aspectCompare)
                .monthCompare(monthCompare)
                .build();
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

    private static String densestAspect(List<String> order, Map<String, List<Evidence>> byAspect, String sentiment) {
        String best = null;
        int bestN = 0;
        List<String> names = (order == null || order.isEmpty()) ? new ArrayList<>(byAspect.keySet()) : order;
        for (String name : names) {
            int n = bySentiment(byAspect.getOrDefault(name, List.of()), sentiment).size();
            if (n > bestN) {
                bestN = n;
                best = name;
            }
        }
        return best;
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

    private static Map<Long, Review> indexReviews(List<Review> countedReviews) {
        Map<Long, Review> byReview = new HashMap<>();
        if (countedReviews != null) {
            for (Review row : countedReviews) {
                if (row.getId() != null) {
                    byReview.put(row.getId(), row);
                }
            }
        }
        return byReview;
    }

    private static String monthKey(Review review) {
        if (review == null || review.getReviewTime() == null) {
            return null;
        }
        return review.getReviewTime().getYear() + "-" + String.format("%02d", review.getReviewTime().getMonthValue());
    }

    private static String monthLabel(String key) {
        if (!StringUtils.hasText(key) || key.length() < 7) {
            return key == null ? "" : key;
        }
        try {
            int year = Integer.parseInt(key.substring(0, 4));
            int month = Integer.parseInt(key.substring(5, 7));
            return year + "年" + month + "月";
        } catch (Exception e) {
            return key;
        }
    }

    private static List<Evidence> inMonth(List<Evidence> rows, Map<Long, Review> byReview, String month) {
        if (rows == null || !StringUtils.hasText(month)) {
            return List.of();
        }
        return rows.stream().filter(row -> month.equals(monthKey(byReview.get(row.getReviewId())))).toList();
    }

    private static Counts countsOf(List<Evidence> rows, Map<Long, Review> byReview) {
        Set<Long> seen = new HashSet<>();
        long pos = 0;
        long neg = 0;
        long neu = 0;
        long volume = 0;
        for (Evidence row : rows) {
            if (row.getReviewId() == null || !seen.add(row.getReviewId())) {
                continue;
            }
            volume++;
            Review review = byReview.get(row.getReviewId());
            String sentiment = review != null && StringUtils.hasText(review.getSentiment())
                    ? review.getSentiment()
                    : row.getSentiment();
            if ("pos".equals(sentiment)) {
                pos++;
            } else if ("neg".equals(sentiment)) {
                neg++;
            } else if ("neu".equals(sentiment)) {
                neu++;
            }
        }
        return new Counts(volume, pos, neg, neu);
    }

    private static ReportView.CompareRow compareRow(String name, Counts counts, List<Evidence> rows) {
        String pain = topPainName(rows);
        return ReportView.CompareRow.builder()
                .name(name)
                .volume(counts.volume)
                .pos(counts.pos)
                .neg(counts.neg)
                .neu(counts.neu)
                .topPain(pain == null ? "—" : pain)
                .build();
    }

    private static String topPainName(List<Evidence> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Evidence row : bySentiment(rows, "neg")) {
            if (!StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            counts.merge(row.getAspectName(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static Map<String, Long> negByAspect(List<Evidence> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Evidence row : bySentiment(rows, "neg")) {
            if (!StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            counts.merge(row.getAspectName(), 1L, Long::sum);
        }
        return counts;
    }

    private static List<Evidence> byAspect(List<Evidence> rows, String aspect) {
        if (rows == null || !StringUtils.hasText(aspect)) {
            return List.of();
        }
        return rows.stream().filter(row -> aspect.equals(row.getAspectName())).toList();
    }

    private record Counts(long volume, long pos, long neg, long neu) {
    }
}
