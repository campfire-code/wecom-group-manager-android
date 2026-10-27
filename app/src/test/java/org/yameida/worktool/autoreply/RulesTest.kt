package org.yameida.worktool.autoreply

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun parseBasicAndMultiKeyword() {
        val r = Rules.parse("价格|多少钱=>示例回复\\n第二行\n# 注释\n\n=你好=>你好呀")
        assertEquals(2, r.rules.size)
        assertEquals(listOf("价格", "多少钱"), r.rules[0].keywords)
        assertEquals("示例回复\n第二行", r.rules[0].reply)
        assertFalse(r.rules[0].exact)
        assertTrue(r.rules[1].exact)
        assertTrue(r.invalidLines.isEmpty())
    }

    @Test fun parseReportsInvalidLines() {
        val r = Rules.parse("没有箭头\n=>只有回复\n关键词=>\n正常=>好")
        assertEquals(1, r.rules.size)
        assertEquals(listOf(1, 2, 3), r.invalidLines)
    }

    @Test fun matchContainsExactAndOrder() {
        val rules = Rules.parse("=你好=>精确\n你好=>包含\nABC=>大写").rules
        assertEquals("精确", Rules.match(rules, "你好")?.reply)
        assertEquals("包含", Rules.match(rules, "你好啊朋友")?.reply)
        assertEquals("大写", Rules.match(rules, "xx abc yy")?.reply)
        assertNull(Rules.match(rules, "没有命中"))
        assertNull(Rules.match(rules, "  "))
    }

    @Test fun roomNameNormalizedAndExact() {
        assertTrue(Rules.roomAllowed("产品交流群(12)", "产品交流群"))
        assertTrue(Rules.roomAllowed("产品交流群…", "产品交流群\n别的群"))
        assertTrue(Rules.roomAllowed("产品交流群（12）", "产品交流群"))
        assertFalse(Rules.roomAllowed("产品交流群二", "产品交流群"))
        assertFalse(Rules.roomAllowed("产品交流群", ""))
        assertFalse(Rules.roomAllowed("", "产品交流群"))
    }

    @Test fun atDetectionAndStrip() {
        assertTrue(Rules.hasAt("@小助手\u2005你好", "小助手"))
        assertFalse(Rules.hasAt("@别人 你好", "小助手"))
        assertFalse(Rules.hasAt("@ 你好", ""))
        assertEquals("你好", Rules.stripAt("@小助手\u2005你好", "小助手"))
    }
}
