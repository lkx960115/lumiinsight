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
            return section("compete", "竞品对照", claims);
        }
        if (rivalEv.isEmpty()) {
            add(claims, "已填写竞品「" + String.join("、", rivals) + "」，但评论商品名里还没有对应条目。请导入竞品评论后再生成。", ids(first(own.isEmpty() ? evidence : own, 3), allowed));
            return section("compete", "竞品对照", claims);
        }
        String ownName = StringUtils.hasText(ownKey) ? ownKey : "本型号";
        String rivalName = String.join("、", rivals);
        Counts ownCounts = countsOf(own, byReview);
        Counts rivalCounts = countsOf(rivalEv, byReview);
        List<ReportView.CompareRow> compare = List.of(
                compareRow(ownName, ownCounts, own),
                compareRow(rivalName, rivalCounts, rivalEv)
        );
        add(claims, ownName + " 原评 " + ownCounts.volume + " 条（正向 " + ownCounts.pos + "、负向 " + ownCounts.neg
                        + "），" + rivalName + " " + rivalCounts.volume + " 条（正向 " + rivalCounts.pos + "、负向 " + rivalCounts.neg + "）。",
                ids(first(all(own, rivalEv), 3), allowed));
        String ownPain = topPainName(own);
        if (ownPain != null) {
            List<Evidence> rows = bySentiment(byAspect(own, ownPain), "neg");
            add(claims, ownName + " 最集中痛点「" + ownPain + "」：「" + quoteOf(rows.get(0)) + "」。", ids(first(rows, 3), allowed));
        }
        String rivalPain = topPainName(rivalEv);
        if (rivalPain != null) {
            List<Evidence> rows = bySentiment(byAspect(rivalEv, rivalPain), "neg");
            add(claims, rivalName + " 最集中痛点「" + rivalPain + "」：「" + quoteOf(rows.get(0)) + "」。", ids(first(rows, 3), allowed));
        }
        addCompeteGaps(claims, ownName, rivalName, own, rivalEv, allowed);
        return section("compete", "竞品对照", claims, compare);
    }

    private void addCompeteGaps(
            List<ReportView.Claim> claims,
            String ownName,
            String rivalName,
            List<Evidence> own,
            List<Evidence> rivalEv,
            Set<Long> allowed
    ) {
        Map<String, Long> ownNeg = negByAspect(own);
        Map<String, Long> rivalNeg = negByAspect(rivalEv);
        Set<String> names = new HashSet<>();
        names.addAll(ownNeg.keySet());
        names.addAll(rivalNeg.keySet());
        String worseOwn = null;
        long worseOwnDelta = 0;
        String worseRival = null;
        long worseRivalDelta = 0;
        for (String name : names) {
            long a = ownNeg.getOrDefault(name, 0L);
            long b = rivalNeg.getOrDefault(name, 0L);
            if (a - b > worseOwnDelta) {
                worseOwnDelta = a - b;
                worseOwn = name;
            }
            if (b - a > worseRivalDelta) {
                worseRivalDelta = b - a;
                worseRival = name;
            }
        }
        if (worseOwn != null && worseOwnDelta > 0) {
            List<Evidence> rows = bySentiment(byAspect(own, worseOwn), "neg");
            add(claims, ownName + " 在「" + worseOwn + "」上负向更多（" + ownNeg.get(worseOwn) + " 对 "
                            + rivalNeg.getOrDefault(worseOwn, 0L) + "），改品应优先这里。",
                    ids(first(rows, 3), allowed));
        }
        if (worseRival != null && worseRivalDelta > 0 && !Objects.equals(worseRival, worseOwn)) {
            List<Evidence> rows = bySentiment(byAspect(rivalEv, worseRival), "neg");
            add(claims, rivalName + " 在「" + worseRival + "」上负向更多，可作对照卖点，仍要核对本型号原评。",
                    ids(first(rows, 3), allowed));
        }
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

    private static ReportView.Section section(String code, String title, List<ReportView.Claim> claims) {
        return section(code, title, claims, null);
    }

    private static ReportView.Section section(String code, String title, List<ReportView.Claim> claims, List<ReportView.CompareRow> compare) {
        return ReportView.Section.builder().code(code).title(title).claims(claims).compare(compare).build();
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
