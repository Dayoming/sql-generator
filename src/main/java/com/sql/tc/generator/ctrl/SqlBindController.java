package com.sql.tc.generator.ctrl;

import com.sql.tc.generator.dto.RenderRequest;
import com.sql.tc.generator.dto.RenderResult;
import com.sql.tc.generator.dto.SqlFragmentNode;
import com.sql.tc.generator.svc.SqlRenderer;
import com.sql.tc.generator.util.MyBatisFragmentParser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SqlBindController {

    private final MyBatisFragmentParser parser;
    private final SqlRenderer renderer;

    public SqlBindController(MyBatisFragmentParser parser, SqlRenderer renderer) {
        this.parser = parser;
        this.renderer = renderer;
    }

    @PostMapping("/parse")
    public SqlFragmentNode parse(@RequestBody Map<String, String> req) {
        return parser.parse(req.get("rawXml"));
    }

    @PostMapping("/render")
    public RenderResult render(@RequestBody RenderRequest req) {
        SqlFragmentNode tree = parser.parse(req.getRawXml()); // 동일 입력 -> 동일 id 재현
        return renderer.render(tree, req.getSelections(), req.getParamValues(), req.getBindStyle());
    }
}
