package com.sql.tc.generator.dto;

import java.util.List;

public record RenderResult(String sql, List<String> params) {
}
