package com.sql.tc.generator.util;

import com.sql.tc.generator.dto.SqlFragmentNode;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.scripting.xmltags.XMLScriptBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Component
public class MyBatisFragmentParser {

    public SqlFragmentNode parse(String rawXml) {
        String wrapped =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
                        "<mapper namespace=\"tmp\">\n" + rawXml + "\n</mapper>";

        XPathParser xPathParser = new XPathParser(wrapped, false, null, new XMLMapperEntityResolver());
        XNode context = xPathParser.evalNode("/mapper/*[1]"); // select/insert/update/delete 첫 태그

        Configuration configuration = new Configuration();
        XMLScriptBuilder builder = new XMLScriptBuilder(configuration, context);
        SqlSource sqlSource = builder.parseScriptNode();
        IdGenerator ids = new IdGenerator();

        // 동적 태그가 하나도 없는 경우 -> RawSqlSource
        if (sqlSource instanceof RawSqlSource) {
            SqlFragmentNode root = new SqlFragmentNode();
            root.id = ids.next();
            root.type = "TEXT";
            root.text = context.getStringBody(); // 원본 텍스트(#{} 포함) 그대로
            root.children = List.of(); // 자식 없음
            return root;
        }

        // 동적 태그가 있는 경우 -> DynamicSqlSource
        Object rootSqlNode = extractRootSqlNode(sqlSource);
        return convert(rootSqlNode, ids);
    }

    private Object extractRootSqlNode(SqlSource sqlSource) {
        try {
            Field f = sqlSource.getClass().getDeclaredField("rootSqlNode");
            f.setAccessible(true);
            return f.get(sqlSource);
        } catch (Exception e) {
            throw new RuntimeException("rootSqlNode 추출 실패: " + sqlSource.getClass(), e);
        }
    }

    private SqlFragmentNode convert(Object sqlNode, IdGenerator ids) {
        if (sqlNode == null) return null;
        String className = sqlNode.getClass().getSimpleName();
        SqlFragmentNode node = new SqlFragmentNode();
        node.id = ids.next();

        switch (className) {
            case "MixedSqlNode" -> {
                node.type = "TEXT"; // 컨테이너, 프론트에서는 children만 순차 출력
                List<Object> contents = getField(sqlNode, "contents");
                for (Object c : contents) node.children.add(convert(c, ids));
            }
            case "StaticTextSqlNode" -> {
                node.type = "TEXT";
                node.text = getField(sqlNode, "text");
            }
            case "TextSqlNode" -> { // ${} 포함 텍스트
                node.type = "TEXT";
                node.text = getField(sqlNode, "text");
            }
            case "IfSqlNode" -> {
                node.type = "IF";
                node.test = getField(sqlNode, "test");
                node.children.add(convert(getField(sqlNode, "contents"), ids));
            }
            case "ChooseSqlNode" -> {
                node.type = "CHOOSE";
                node.whens = new ArrayList<>();
                for (Object w : (List<Object>) getField(sqlNode, "ifSqlNodes")) {
                    SqlFragmentNode whenNode = convert(w, ids);
                    whenNode.type = "WHEN"; // <when>은 <if>와 달리 자체 선택 플래그가 필요 없음
                    node.whens.add(whenNode);
                }
                Object def = getField(sqlNode, "defaultSqlNode");
                node.otherwise = convert(unwrapOptional(def), ids);
            }
            case "TrimSqlNode", "WhereSqlNode", "SetSqlNode" -> {
                node.type = className.equals("WhereSqlNode") ? "WHERE"
                        : className.equals("SetSqlNode") ? "SET" : "TRIM";
                node.prefix = getField(sqlNode, "prefix");
                node.prefixOverrides = getField(sqlNode, "prefixesToOverride");
                node.children.add(convert(getField(sqlNode, "contents"), ids));
            }
            case "ForEachSqlNode" -> {
                node.type = "FOREACH";
                node.collection = getField(sqlNode, "collectionExpression");
                node.item = getField(sqlNode, "item");
                node.index = getField(sqlNode, "index");
                node.open = getField(sqlNode, "open");
                node.close = getField(sqlNode, "close");
                node.separator = getField(sqlNode, "separator");
                node.children.add(convert(getField(sqlNode, "contents"), ids));
            }
            case "VarDeclSqlNode" -> { // <bind>
                node.type = "BIND";
                node.text = getField(sqlNode, "name") + "=" + getField(sqlNode, "expression");
            }
            default -> { node.type = "TEXT"; node.text = ""; }
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object target, String name) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass(); // 현재 클래스에 없으면 부모 클래스로 올라가서 다시 시도
            } catch (IllegalAccessException e) {
                throw new RuntimeException("field 접근 실패: " + name, e);
            }
        }
        throw new RuntimeException(
                "field 추출 실패: " + name + " (클래스 " + target.getClass().getSimpleName() +
                        " 및 상위 클래스에 없음. MyBatis 버전에 따라 필드명이 다를 수 있습니다)"
        );
    }

    private Object unwrapOptional(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Optional<?> opt) {
            return opt.orElse(null);
        }
        return value; // 이미 SqlNode(또는 null)인 경우 그대로 반환
    }
}
