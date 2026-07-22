package com.sql.tc.generator.dto;

import java.util.ArrayList;
import java.util.List;

public class SqlFragmentNode {
    public String id;              // 파싱 시 DFS 순서로 결정론적 부여 (n0, n1, n2...)
    public String type;            // TEXT | IF | CHOOSE | WHEN | OTHERWISE | FOREACH | TRIM | WHERE | SET
    public String text;            // TEXT일 때 원본 문자열 (#{}, ${} 그대로 포함)
    public String test;            // IF/WHEN의 test 표현식 (화면 표시용, 평가는 안함)
    public List<SqlFragmentNode> children = new ArrayList<>(); // MIXED/IF/TRIM 등의 자식들

    // CHOOSE 전용
    public List<SqlFragmentNode> whens;
    public SqlFragmentNode otherwise;

    // TRIM/WHERE/SET 전용
    public String prefix;
    public List<String> prefixOverrides;
    public String suffix;
    public List<String> suffixOverrides;

    // FOREACH 전용
    public String collection, item, index, open, close, separator;
}
