package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CodeGraph 文本解析器契约测试：锁定 v1.5.0 文本格式，未知/畸形 fail closed，无结果返回空。
 */
class CodeGraphResponseParserTest {

    private CodeGraphResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new CodeGraphResponseParser();
    }

    @Test
    void parsesSearch() {
        String text = "**Search Results (2 found)**\n\n"
                + "**createDevice** (method)\nAssetController.java:10\n`public void createDevice()`\n\n"
                + "**create** (method)\nAssetService.java:8\n`public void create()`\n\n";

        List<Symbol> symbols = parser.parseSearch(text);

        assertThat(symbols).hasSize(2);
        assertThat(symbols.get(0).name()).isEqualTo("createDevice");
        assertThat(symbols.get(0).kind()).isEqualTo("method");
        assertThat(symbols.get(0).filePath()).isEqualTo("AssetController.java");
        assertThat(symbols.get(0).lineNumber()).isEqualTo(10);
        assertThat(symbols.get(1).name()).isEqualTo("create");
        assertThat(symbols.get(1).filePath()).isEqualTo("AssetService.java");
    }

    @Test
    void parsesCallers() {
        String text = "**Callers of AssetRepository.insert (1 found)**\n\n"
                + "- AssetService.create (method) - AssetService.java:8\n";

        List<Symbol> symbols = parser.parseCallerCallees(text);

        assertThat(symbols).hasSize(1);
        assertThat(symbols.get(0).name()).isEqualTo("AssetService.create");
        assertThat(symbols.get(0).kind()).isEqualTo("method");
        assertThat(symbols.get(0).filePath()).isEqualTo("AssetService.java");
        assertThat(symbols.get(0).lineNumber()).isEqualTo(8);
    }

    @Test
    void parsesCallees() {
        String text = "**Callees of AssetService.create (1 found)**\n\n"
                + "- AssetRepository.insert (method) - AssetRepository.java:6\n";

        List<Symbol> symbols = parser.parseCallerCallees(text);

        assertThat(symbols).hasSize(1);
        assertThat(symbols.get(0).name()).isEqualTo("AssetRepository.insert");
        assertThat(symbols.get(0).filePath()).isEqualTo("AssetRepository.java");
    }

    @Test
    void parsesImpact() {
        String text = "**Impact: \"AssetRepository.insert\" affects 2 symbols**\n\n"
                + "**AssetService.java:**\nAssetService.create:8, AssetController.createDevice:10\n\n";

        List<Symbol> symbols = parser.parseImpact(text);

        assertThat(symbols).hasSize(2);
        assertThat(symbols.get(0).name()).isEqualTo("AssetService.create");
        assertThat(symbols.get(0).filePath()).isEqualTo("AssetService.java");
        assertThat(symbols.get(0).lineNumber()).isEqualTo(8);
        assertThat(symbols.get(1).name()).isEqualTo("AssetController.createDevice");
    }

    @Test
    void parsesCallChainFromExplore() {
        String text = "Found 2 symbols across 1 file.\n\n"
                + "- AssetController.createDevice (method) - AssetController.java:10\n"
                + "- AssetService.create (method) - AssetService.java:8\n";

        List<CallStep> steps = parser.parseCallChain(text);

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).symbol()).isEqualTo("AssetController.createDevice");
        assertThat(steps.get(1).symbol()).isEqualTo("AssetService.create");
    }

    @Test
    void degradesCallChainWhenUnparseable() {
        assertThat(parser.parseCallChain("Found 0 symbols across 0 files.")).isEmpty();
        assertThat(parser.parseCallChain("some unrelated prose")).isEmpty();
    }

    @Test
    void parsesClassHierarchyFromSignature() {
        String text = "**AssetServiceImpl** (class)\n\n**Location:** AssetServiceImpl.java:5\n\n"
                + "**Signature:** `class AssetServiceImpl extends BaseService implements AssetReader`\n";

        ClassHierarchy hierarchy = parser.parseClassHierarchy(text, "AssetServiceImpl");

        assertThat(hierarchy.className()).isEqualTo("AssetServiceImpl");
        assertThat(hierarchy.ancestors()).containsExactly("BaseService", "AssetReader");
    }

    @Test
    void degradesClassHierarchyWhenUnparseable() {
        ClassHierarchy hierarchy = parser.parseClassHierarchy("no signature here", "AssetServiceImpl");
        assertThat(hierarchy.ancestors()).isEmpty();
    }

    @Test
    void noResultReturnsEmpty() {
        assertThat(parser.parseSearch("No results found for \"x\"")).isEmpty();
        assertThat(parser.parseCallerCallees("Symbol \"x\" not found in the codebase")).isEmpty();
        assertThat(parser.parseCallerCallees("No callers found for \"x\"")).isEmpty();
        assertThat(parser.parseCallerCallees("No callees found for \"x\"")).isEmpty();
    }

    @Test
    void unknownSearchFormatFailsClosed() {
        assertThatThrownBy(() -> parser.parseSearch("<html>garbage</html>"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    @Test
    void unknownCallerCalleesFormatFailsClosed() {
        assertThatThrownBy(() -> parser.parseCallerCallees("totally unknown format"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    @Test
    void unknownImpactFormatFailsClosed() {
        assertThatThrownBy(() -> parser.parseImpact("not an impact report"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    @Test
    void exposesFormatVersion() {
        assertThat(parser.formatVersion()).isEqualTo(CodeGraphResponseParser.FORMAT_VERSION);
    }
}
