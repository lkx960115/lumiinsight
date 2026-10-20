package com.lumiinsight.modules.pipeline;

public final class PipelineCopy {

    private PipelineCopy() {
    }

    public static String cleanFailed(Throwable error) {
        return failed(error, true);
    }

    public static String analyzeFailed(Throwable error) {
        return failed(error, false);
    }

    public static String indexNote(Throwable error) {
        String blob = blob(error);
        if (unauthorized(blob)) {
            return "模型未授权，暂用关键词";
        }
        if (unreachable(blob)) {
            return "分析服务暂时连不上，暂用关键词";
        }
        if (timeout(blob)) {
            return "向量写入超时，暂用关键词";
        }
        return "向量未写入，暂用关键词";
    }

    public static String noCleanResult() {
        return "分析服务没有返回清洗结果，请重试。";
    }

    public static String noAnalyzeResult() {
        return "分析服务没有返回分析结果，请重试。";
    }

    private static String failed(Throwable error, boolean clean) {
        String blob = blob(error);
        if (unreachable(blob)) {
            return "分析服务暂时连不上。请先启动分析进程，再点重试。";
        }
        if (timeout(blob)) {
            return clean ? "清洗超时，请稍后重试。" : "分析超时，请稍后重试。评论很多时可先少导一些。";
        }
        if (unauthorized(blob)) {
            return "模型未授权。请到系统管理重新粘贴密钥，再点重试。";
        }
        if (noResult(blob)) {
            return clean ? noCleanResult() : noAnalyzeResult();
        }
        return clean ? "清洗没有完成，请重试。" : "分析没有完成，请重试。";
    }

    private static boolean unreachable(String blob) {
        return blob.contains("connection refused")
                || blob.contains("connect timed out")
                || blob.contains("connection reset")
                || blob.contains("failed to connect")
                || blob.contains("unknownhost")
                || blob.contains("nohttpresponse")
                || blob.contains("worker 不可用");
    }

    private static boolean timeout(String blob) {
        return blob.contains("timeout") || blob.contains("timed out") || blob.contains("超时");
    }

    private static boolean unauthorized(String blob) {
        return blob.contains("401") || blob.contains("unauthorized") || blob.contains("未授权");
    }

    private static boolean noResult(String blob) {
        return blob.contains("未返回")
                || blob.contains("没有返回")
                || blob.contains("extracting response")
                || blob.contains("httpmessageconverter");
    }

    private static String blob(Throwable error) {
        if (error == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth < 6) {
            sb.append(' ').append(cursor.getClass().getName());
            if (cursor.getMessage() != null) {
                sb.append(' ').append(cursor.getMessage());
            }
            cursor = cursor.getCause();
            depth++;
        }
        return sb.toString().toLowerCase();
    }
}
