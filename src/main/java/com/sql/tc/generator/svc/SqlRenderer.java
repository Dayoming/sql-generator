package com.sql.tc.generator.svc;

import com.sql.tc.generator.dto.RenderResult;
import com.sql.tc.generator.dto.SqlFragmentNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlRenderer {

    public RenderResult render(SqlFragmentNode node, Map<String, Object> selections,
                               Map<String, String> paramValues, String bindStyle) {
        StringBuilder sb = new StringBuilder();
        assemble(node, selections, sb);

        String assembled = sb.toString();

        // 1. ${} 리터럴 치환 (SQL Injection 위험 있음을 UI에 명시)
        for (var e : paramValues.entrySet()) {
            assembled = assembled.replace("${" + e.getKey() + "}", e.getValue());
        }

        // 2. #{...} 치환 (NAMED: :name / POSITIONAL: ?)
        boolean positional = "POSITIONAL".equalsIgnoreCase(bindStyle);

        List<String> params = new ArrayList<>();
        Matcher m = Pattern.compile("#\\{([a-zA-Z0-9_.]+)(?:,[^}]*)?}").matcher(assembled);
        StringBuilder finalSql = new StringBuilder();
        while (m.find()) {
            params.add(m.group(1));
            String replacement = positional ? "?" : (":" + m.group(1));
            m.appendReplacement(finalSql, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(finalSql);

        String cleaned = finalSql.toString()
                .replaceAll("(?m)^[ \\t]*\\r?\\n", "")  // 내용 없이 공백/탭만 있는 줄을 통째로 삭제
                .replaceAll("\\n{2,}", "\n")            // 혹시 남는 연속 개행 정리 (안전망)
                .trim();

        return new RenderResult(cleaned, params);
    }

    private void assemble(SqlFragmentNode node, Map<String, Object> sel, StringBuilder sb) {
        if (node == null) return;
        switch (node.type) {
            case "TEXT" -> {
                if (node.text != null) sb.append(node.text); // 가공 없이 그대로 append
                node.children.forEach(c -> assemble(c, sel, sb));
            }
            case "IF" -> {
                Object checked = sel.get(node.id);
                if (Boolean.TRUE.equals(checked))
                    node.children.forEach(c -> assemble(c, sel, sb));
            }
            case "WHEN" -> {
                // CHOOSE가 이미 이 when을 선택했기 때문에 별도 조건 체크 없이 바로 출력
                node.children.forEach(c -> assemble(c, sel, sb));
            }
            case "CHOOSE" -> {
                String rawChosen = (String) sel.get(node.id);
                String chosenId = (rawChosen instanceof String s) ? s : null;
                if (chosenId == null) {
                    // 아무 것도 선택하지 않았으면 CHOOSE 블록 전체를 출력하지 않음
                    break;
                }
                node.whens.stream()
                        .filter(w -> w.id.equals(chosenId))
                        .findFirst()
                        .or(() -> node.otherwise != null && node.otherwise.id.equals(chosenId)
                                ? Optional.of(node.otherwise)
                                : Optional.empty())
                        .ifPresent(w -> assemble(w, sel, sb));
            }
            case "WHERE", "TRIM", "SET" -> {
                StringBuilder inner = new StringBuilder();
                node.children.forEach(c -> assemble(c, sel, inner));
                String body = inner.toString().trim();
                if (!body.isEmpty()) {
                    // 앞쪽 접두어 제거 (예: WHERE의 AND/OR)
                    if (node.prefixOverrides != null) {
                        for (String ov : node.prefixOverrides) {
                            String trimmedOv = ov.trim();
                            if (!trimmedOv.isEmpty() && body.startsWith(trimmedOv)) {
                                body = body.substring(trimmedOv.length()).trim();
                                break;
                            }
                        }
                    }
                    // 뒤쪽 접미어 제거 (예: SET의 마지막 콤마)
                    if (node.suffixOverrides != null) {
                        for (String ov : node.suffixOverrides) {
                            String trimmedOv = ov.trim();
                            if (!trimmedOv.isEmpty() && body.endsWith(trimmedOv)) {
                                body = body.substring(0, body.length() - trimmedOv.length()).trim();
                                break;
                            }
                        }
                    }

                    String prefix = node.prefix != null ? node.prefix : "";
                    String suffix = node.suffix != null ? node.suffix : "";
                    sb.append(prefix).append(" ").append(body).append(" ").append(suffix).append(" ");
                }
            }
            case "FOREACH" -> {
                Object raw = sel.getOrDefault("foreach_" + node.id, "0");
                int count = Integer.parseInt(String.valueOf(raw).trim());

                String open = node.open != null ? node.open : "";
                String close = node.close != null ? node.close : "";
                String separator = node.separator != null ? node.separator : "";

                List<String> iterTexts = new ArrayList<>();
                for (int i = 1; i <= count; i++) {
                    StringBuilder iter = new StringBuilder();
                    node.children.forEach(c -> assemble(c, sel, iter));

                    // 반복 항목 하나는 줄바꿈/들여쓰기 없이 한 줄로 압축
                    String iterText = iter.toString()
                            .replace("#{" + node.item, "#{" + node.item + i)
                            .replaceAll("\\s+", " ")
                            .trim();
                    iterTexts.add(iterText);
                }

                String body = String.join(separator + " ", iterTexts); // "?, ?, ?"
                if (count > 0) sb.append(open).append(body).append(close);
            }
        }
    }
}
